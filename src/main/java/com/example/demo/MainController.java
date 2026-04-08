package com.example.demo;

import com.example.demo.entity.*;
import com.example.demo.service.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import org.springframework.validation.BindingResult;

@Controller
public class MainController {

    private final ArticleService articleService;
    private final UserService userService;
    private final TopicService topicService;
    private final CommentService commentService;
    private final LikeService likeService;
    private final PasswordEncoder passwordEncoder;

    public MainController(ArticleService articleService, UserService userService,
                          TopicService topicService, CommentService commentService,
                          LikeService likeService, PasswordEncoder passwordEncoder) {
        this.articleService = articleService;
        this.userService = userService;
        this.topicService = topicService;
        this.commentService = commentService;
        this.likeService = likeService;
        this.passwordEncoder = passwordEncoder;
    }

    // ✅ Trang login
    // Hiển thị form login
    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("user", new User()); // để binding lỗi nếu cần
        return "login"; // render login.html
    }

    // Xử lý submit form login
    @PostMapping("/login")
    public String loginSubmit(@RequestParam String username,
                              @RequestParam String password,
                              HttpSession session,
                              Model model) {
        // Tìm user theo username
        User user = userService.findByUsername(username)
                .orElse(null);

        if (user == null) {
            model.addAttribute("loginError", "Tên đăng nhập không tồn tại");
            return "login";
        }

        // Kiểm tra mật khẩu
        if (!passwordEncoder.matches(password, user.getPassword())) {
            model.addAttribute("loginError", "Mật khẩu không đúng");
            return "login";
        }

        // Đăng nhập thành công → lưu user vào session
        session.setAttribute("currentUser", user);

        // Chuyển hướng về trang chủ
        if (user.getRole() == Role.ADMIN) {
            return "redirect:/admin";  // admin → admin_home.html
        } else {
            return "redirect:/";            // user thường → home.html
        }
    }

    // ✅ Trang đăng ký
    // Hiển thị form đăng ký
    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("user", new User());
        return "register"; // render register.html
    }

    @PostMapping("/register")
    public String registerSubmit(@ModelAttribute("user") User user,
                                 BindingResult result,
                                 Model model) {
        // Kiểm tra xác nhận mật khẩu
        if (!user.getPassword().equals(user.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "error.user", "Mật khẩu xác nhận không khớp");
        }

        // Kiểm tra username trùng
        if (userService.existsByUsername(user.getUsername())) {
            result.rejectValue("username", "error.user", "Username đã tồn tại");
        }

        // Nếu có lỗi thì quay lại form
        if (result.hasErrors()) {
            return "register";
        }

        // Mã hoá mật khẩu trước khi lưu
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Lưu user vào DB
        userService.save(user);

        // Sau khi đăng ký thành công → chuyển về trang login
        return "redirect:/login";
    }


    // 🏠 Trang chủ
    @GetMapping("/")
    public String home(HttpServletRequest request,
                       HttpSession session,
                       Model model,
                       @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page) {

        String form = request.getParameter("form");

        // Nếu không có tham số GET => hiển thị danh sách bài viết mới nhất
        if (form == null) {
            Page<Article> pageObj = articleService.findAll(PageRequest.of(page, 10));
            model.addAttribute("posts", pageObj.getContent());
            model.addAttribute("pageObj", pageObj);
            return "home"; // render home.html
        }

        // Nếu có tham số GET => xử lý theo giá trị form
        switch (form) {
            case "search":
                String keyword = request.getParameter("keyword");
                Page<Article> searchResults = articleService.findByTitleContainingIgnoreCase(keyword, PageRequest.of(page, 10));
                model.addAttribute("posts", searchResults.getContent());
                model.addAttribute("pageObj", searchResults);
                return "home";

            case "logout":
                session.invalidate();
                return "redirect:/login";

            case "create":
                return "redirect:/articles/new";

            case "filter":
                return "redirect:/filter";

            case "user":
                return "redirect:/user";

            case "BXH":
                return "redirect:/users/rank";

            default:
                Page<Article> defaultPage = articleService.findAll(PageRequest.of(page, 10));
                model.addAttribute("posts", defaultPage.getContent());
                model.addAttribute("pageObj", defaultPage);
                return "home";
        }
    }
    
    // ✅ Danh sách user theo mark
    @GetMapping("/users/rank")
    public String userRank(Model model) {
        // Lấy danh sách user theo mark giảm dần
        List<User> users = userService.findAllOrderByMarkDesc();

        // Truyền dữ liệu cho template users_list.html
        model.addAttribute("users", users);

        return "users/users_list"; // render file users/users_list.html
    }

    // ✅ Chi tiết user
    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable Long id, Model model) {
        User user = userService.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        return "users/user";
    }

    // 👤 Trang cá nhân (lấy từ session)
    @GetMapping("/user")
    public String userPage(HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("currentUser");
        if (sessionUser == null) {
            return "redirect:/login";
        }
        
        /*
        // Nếu articles chưa được nạp, khởi tạo rỗng
        if (user.getArticles() == null) {
            user.setArticles(new ArrayList<>());
        }
        */
        
        // Nạp lại từ DB để có đầy đủ quan hệ articles
        User user = userService.findById(sessionUser.getId())
                               .orElseThrow(() -> new RuntimeException("User not found"));

        model.addAttribute("user", user);
        return "users/user";
    }

    // Hiển thị form tạo bài viết
    @GetMapping("/articles/new")
    public String showCreateForm(Model model) {
        model.addAttribute("article", new Article());
        List<Topic> topics = topicService.findAll();
        model.addAttribute("topics", topics);
        return "articles/create"; // render file articles/create.html
    }

    // Xử lý submit form tạo bài viết
    @PostMapping("/articles")
    public String createArticle(@ModelAttribute("article") Article article,
                                HttpSession session,
                                @RequestParam(value = "topics", required = false) List<Long> topicIds) {
        // Lấy user hiện tại từ session
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return "redirect:/login"; // chưa đăng nhập thì về login
        }

        // Gán thông tin cho bài viết
        article.setAuthor(currentUser);
        article.setCreatedAt(LocalDateTime.now());

        // Gán chủ đề
        if (topicIds != null) {
            List<Topic> selectedTopics = topicService.findByIds(topicIds);
            article.setTopics(selectedTopics);
        }

        // Lưu bài viết
        articleService.save(article);

        return "redirect:/"; // sau khi tạo xong quay về trang chủ
    }
    
    // 📝 Chi tiết bài viết
    @GetMapping("/articles/{id}")
    public String articleDetail(@PathVariable Long id,
                                HttpSession session,
                                Model model) {
        // Lấy bài viết
        Article article = articleService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết với id: " + id));

        // Lấy danh sách bình luận theo thời gian giảm dần
        List<Comment> comments = commentService.findByArticleOrderByCreatedAtDesc(article);

        // Kiểm tra người dùng hiện tại đã thích bài viết chưa
        boolean userLikedArticle = false;
        boolean isAuthenticated = false;

        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser != null) {
            isAuthenticated = true;
            userLikedArticle = likeService.existsByUserAndArticle(currentUser, article);
        }

        // Truyền dữ liệu cho template
        model.addAttribute("article", article);
        model.addAttribute("comments", comments);
        model.addAttribute("userLikedArticle", userLikedArticle);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "articles/article"; // render file articles/article.html
    }

    // 💬 Tạo comment
        @PostMapping("/articles/{id}/comments")
    public String createComment(@PathVariable Long id,
                                @RequestParam("content") String content,
                                HttpSession session) {
        // Lấy user hiện tại từ session
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return "redirect:/login"; // chưa đăng nhập thì về login
        }

        // Lấy bài viết
        Article article = articleService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết với id: " + id));

        // Tạo comment mới
        Comment comment = new Comment();
        comment.setAuthor(currentUser);
        comment.setArticle(article);
        comment.setContent(content);

        // Lưu comment
        commentService.save(comment);

        // Quay lại trang chi tiết bài viết
        return "redirect:/articles/" + id;
    }

    // ❤️ Like bài viết
    @PostMapping("/articles/{id}/like")
    public String likeArticle(@PathVariable Long id, HttpSession session) {
        // Lấy user hiện tại từ session
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return "redirect:/login"; // chưa đăng nhập thì về login
        }

        // Lấy bài viết
        Article article = articleService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết với id: " + id));

        // Kiểm tra xem user đã like chưa
        Like existingLike = likeService.findByUserAndArticle(currentUser, article);

        if (existingLike != null) {
            // Nếu đã like, xóa like đi
            likeService.delete(existingLike);
            
            // Giảm điểm cho tác giả bài viết
            User author = article.getAuthor();
            author.setMark(author.getMark() - 1);
            userService.save(author);
        } else {
            // Nếu chưa like, tạo like mới
            Like newLike = new Like();
            newLike.setUser(currentUser);
            newLike.setArticle(article);
            likeService.save(newLike);

            // Tăng điểm cho tác giả bài viết
            User author = article.getAuthor();
            author.setMark(author.getMark() + 1);
            userService.save(author);
            // lưu lại user author (nếu có UserService thì gọi save)
        }

        // Quay lại trang chi tiết bài viết
        return "redirect:/articles/" + id;
    }
    
    // tìm kiếm
    @GetMapping("/search")
    public String search(@RequestParam(value = "keyword", required = false) String keyword,
                         @RequestParam(value = "page_articles", defaultValue = "0") int pageArticles,
                         @RequestParam(value = "page_users", defaultValue = "0") int pageUsers,
                         Model model) {

        String k = (keyword != null) ? keyword.trim() : "";

        Page<Article> articles;
        Page<User> users;

        if (!k.isEmpty()) {
            articles = articleService.findByTitleContainingIgnoreCase(k, PageRequest.of(pageArticles, 10));
            users = userService.findByNameContainingIgnoreCase(k, PageRequest.of(pageUsers, 9));
        } else {
            articles = Page.empty();
            users = Page.empty();
        }

        model.addAttribute("articles", articles);
        model.addAttribute("users", users);
        model.addAttribute("keyword", k);

        return "search";
    }
    
    // lọc
    @GetMapping("/filter")
    public String filter(@RequestParam(value = "topics", required = false) List<Long> selectedTopics,
                         Model model) {
        // Lấy tất cả chủ đề để hiển thị checkbox
        List<Topic> topics = topicService.findAll();

        // Nếu có chọn chủ đề thì lọc bài viết, ngược lại trả về rỗng
        List<Article> articles;
        if (selectedTopics != null && !selectedTopics.isEmpty()) {
            articles = articleService.findByTopicIds(selectedTopics);
        } else {
            articles = List.of();
        }

        model.addAttribute("topics", topics);
        model.addAttribute("articles", articles);
        model.addAttribute("selectedTopics", selectedTopics != null ? selectedTopics : List.of());

        return "filter";
    }
}



/*
package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class MainController {
    private final UserService userService;
    private final ArticleService articleService;
    private final TopicService topicService;
    private final CommentService commentService;
    private final LikeService likeService;

    public MainController(UserService userService,
                          ArticleService articleService,
                          TopicService topicService,
                          CommentService commentService,
                          LikeService likeService) {
        this.userService = userService;
        this.articleService = articleService;
        this.topicService = topicService;
        this.commentService = commentService;
        this.likeService = likeService;
    }

    // 🏠 Trang chủ
    @GetMapping("/")
    public String home(HttpServletRequest request,
                       HttpSession session,
                       Model model,
                       @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page) {

        String form = request.getParameter("form");

        // Nếu không có tham số GET => hiển thị danh sách bài viết mới nhất
        if (form == null) {
            Page<Article> pageObj = articleService.findAll(PageRequest.of(page, 10));
            model.addAttribute("posts", pageObj.getContent());
            model.addAttribute("pageObj", pageObj);
            return "home"; // render home.html
        }

        // Nếu có tham số GET => xử lý theo giá trị form
        switch (form) {
            case "search":
                String keyword = request.getParameter("keyword");
                Page<Article> searchResults = articleService.search(keyword, PageRequest.of(page, 10));
                model.addAttribute("posts", searchResults.getContent());
                model.addAttribute("pageObj", searchResults);
                return "home";

            case "logout":
                session.invalidate();
                return "redirect:/login";

            case "create":
                return "redirect:/articles/new";

            case "filter":
                return "redirect:/filter";

            case "user":
                return "redirect:/user";

            case "BXH":
                return "redirect:/users/rank";

            default:
                Page<Article> defaultPage = articleService.findAll(PageRequest.of(page, 10));
                model.addAttribute("posts", defaultPage.getContent());
                model.addAttribute("pageObj", defaultPage);
                return "home";
        }
    }

    // 👤 Danh sách người dùng
    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("users", userService.findAll());
        return "user_list";
    }

    // 👤 Chi tiết người dùng
    @GetMapping("/users/{id}")
    public String getUser(@PathVariable Long id, Model model) {
        User user = userService.findById(id).orElseThrow();
        model.addAttribute("user", user);
        return "user";
    }

    // 📝 Danh sách bài viết
    @GetMapping("/articles")
    public String listArticles(Model model) {
        model.addAttribute("articles", articleService.findAll());
        return "article_list";
    }

    // 📝 Chi tiết bài viết
    @GetMapping("/articles/{id}")
    public String getArticle(@PathVariable Long id, Model model) {
        Article article = articleService.findById(id).orElseThrow();
        model.addAttribute("article", article);
        return "article";
    }

    // 📝 Form tạo bài viết
    @GetMapping("/articles/create")
    public String createForm(Model model) {
        model.addAttribute("article", new Article());
        model.addAttribute("topics", topicService.findAll());
        return "create";
    }

    // 📝 Lưu bài viết
    @PostMapping("/articles")
    public String saveArticle(@ModelAttribute Article article) {
        articleService.save(article);
        return "redirect:/articles";
    }

    // 💬 Thêm bình luận
    @PostMapping("/comments")
    public String saveComment(@ModelAttribute Comment comment) {
        commentService.save(comment);
        return "redirect:/articles/" + comment.getArticle().getId();
    }

    // ❤️ Thêm lượt thích
    @PostMapping("/likes")
    public String saveLike(@ModelAttribute Like like) {
        likeService.save(like);
        return "redirect:/articles/" + like.getArticle().getId();
    }

    // 📚 Danh sách chủ đề
    @GetMapping("/topics")
    public String listTopics(Model model) {
        model.addAttribute("topics", topicService.findAll());
        return "topic_list";
    }
}

*/
