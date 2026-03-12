package com.logstream.controller;

import com.logstream.dto.LogSearchRequest;
import com.logstream.model.RetentionPolicy;
import com.logstream.service.LogImportService;
import com.logstream.service.AuthService;
import com.logstream.service.HealthService;
import com.logstream.service.IngestionService;
import com.logstream.service.RetentionService;
import com.logstream.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebController {

    private final AnalyticsService analyticsService;
    private final HealthService healthService;
    private final IngestionService ingestionService;
    private final RetentionService retentionService;
    private final SearchService searchService;
    private final AuthService authService;
    private final LogImportService logImportService;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, Model model) {
        model.addAttribute("loginError", error);
        return "login";
    }

    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return "redirect:/login?loggedOut=true";
    }

    @GetMapping("/logout-page")
    public String logoutPage(HttpServletRequest request, HttpServletResponse response) {
        return logout(request, response);
    }

    @GetMapping("/404")
    public String notFound() {
        return "404";
    }

    @GetMapping("/access-denied")
    public String accessDenied(@RequestParam(required = false) String message, Model model) {
        model.addAttribute("message", message);
        return "access-denied";
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("sidebarCollapsed", false);
        model.addAttribute("isAdmin", isAdmin());
        
        var healthDashboard = healthService.getHealthDashboard();
        
        model.addAttribute("totalServices", healthDashboard.size());
        model.addAttribute("healthyCount", healthDashboard.stream()
                .filter(h -> "GREEN".equals(h.getStatus())).count());
        model.addAttribute("degradedCount", healthDashboard.stream()
                .filter(h -> "YELLOW".equals(h.getStatus())).count());
        model.addAttribute("unhealthyCount", healthDashboard.stream()
                .filter(h -> "RED".equals(h.getStatus())).count());
        return "dashboard";
    }

    private boolean isAdmin() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getAuthorities() == null) {
                return false;
            }
            String userRole = auth.getAuthorities()
                    .stream().findFirst().map(auth1 -> auth1.getAuthority()).orElse("USER");
            return "ROLE_ADMIN".equals(userRole) || "ADMIN".equals(userRole);
        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping("/retention")
    public String retentionPolicies(
            @RequestParam(required = false) String success,
            @RequestParam(required = false) String error,
            Model model) {
        model.addAttribute("pageTitle", "Retention Policies");
        model.addAttribute("sidebarCollapsed", false);
        model.addAttribute("isAdmin", isAdmin());
        
        if (success != null) {
            model.addAttribute("success", success);
        }
        if (error != null) {
            model.addAttribute("error", error);
        }
        
        try {
            model.addAttribute("policies", retentionService.getPolicies());
            model.addAttribute("services", retentionService.getAllServices());
        } catch (Exception e) {
            model.addAttribute("policies", java.util.Collections.emptyList());
            model.addAttribute("services", java.util.Collections.emptyList());
            model.addAttribute("error", "Unable to load policies: " + e.getMessage());
        }
        
        model.addAttribute("newPolicy", new RetentionPolicy());
        return "retention";
    }

    @PostMapping("/retention/add")
    public String addRetentionPolicy(@ModelAttribute RetentionPolicy policy, RedirectAttributes redirectAttributes) {
        try {
            retentionService.updatePolicy(
                policy.getServiceName(),
                policy.getRetentionDays(),
                policy.isArchiveEnabled()
            );
            redirectAttributes.addFlashAttribute("success", "Retention policy created successfully");
        } catch (UnsupportedOperationException e) {
            redirectAttributes.addFlashAttribute("error", "Feature not yet implemented: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create policy: " + e.getMessage());
        }
        return "redirect:/retention";
    }

    @GetMapping("/retention/edit/{serviceName}")
    public String editRetentionPolicy(@PathVariable("serviceName") String serviceName, Model model) {
        RetentionPolicy policy = retentionService.getPolicyByServiceName(serviceName);
        if (policy == null) {
            model.addAttribute("error", "Policy not found for service: " + serviceName);
            model.addAttribute("policies", retentionService.getPolicies());
            return "retention";
        }
        model.addAttribute("pageTitle", "Edit Retention Policy");
        model.addAttribute("sidebarCollapsed", false);
        model.addAttribute("isAdmin", isAdmin());
        model.addAttribute("policy", policy);
        model.addAttribute("policies", retentionService.getPolicies());
        return "edit-retention";
    }

    @PostMapping("/retention/update/{serviceName}")
    public String updateRetentionPolicy(@PathVariable("serviceName") String serviceName, @ModelAttribute RetentionPolicy policy, RedirectAttributes redirectAttributes) {
        try {
            retentionService.updatePolicy(serviceName, policy.getRetentionDays(), policy.isArchiveEnabled());
            redirectAttributes.addFlashAttribute("success", "Retention policy updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update policy: " + e.getMessage());
        }
        return "redirect:/retention";
    }

    @GetMapping("/retention/delete/{serviceName}")
    public String deleteRetentionPolicy(@PathVariable("serviceName") String serviceName, RedirectAttributes redirectAttributes) {
        try {
            retentionService.deletePolicy(serviceName);
            redirectAttributes.addFlashAttribute("success", "Retention policy deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete policy: " + e.getMessage());
        }
        return "redirect:/retention";
    }

    @GetMapping("/logs")
    public String logManagement(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search,
            Model model) {
        model.addAttribute("pageTitle", "Log Management");
        model.addAttribute("sidebarCollapsed", false);
        model.addAttribute("isAdmin", isAdmin());
        
        LogSearchRequest searchRequest = LogSearchRequest.builder()
                .serviceName(service)
                .keyword(search)
                .page(page)
                .size(20)
                .build();
        
        if (level != null && !level.isEmpty()) {
            try {
                searchRequest.setLevel(com.logstream.model.LogLevel.valueOf(level));
            } catch (IllegalArgumentException e) {
                // Invalid log level, ignore
            }
        }
        
        try {
            var logs = searchService.searchLogs(searchRequest);
            model.addAttribute("logs", logs);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", logs.getTotalPages());
        } catch (Exception e) {
            model.addAttribute("logs", null);
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("error", "Unable to load logs: " + e.getMessage());
        }
        
        try {
            model.addAttribute("services", analyticsService.getErrorRatePerService());
        } catch (Exception e) {
            model.addAttribute("services", java.util.Collections.emptyList());
        }
        
        return "logs";
    }

    @GetMapping("/logs/import")
    public String importLogsForm(Model model) {
        model.addAttribute("pageTitle", "Import Logs");
        model.addAttribute("sidebarCollapsed", false);
        model.addAttribute("isAdmin", isAdmin());
        return "import-logs";
    }

    @GetMapping("/logs-data")
    public String getLogsFragment(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search,
            Model model) {
        
        LogSearchRequest searchRequest = LogSearchRequest.builder()
                .serviceName(service)
                .keyword(search)
                .page(page)
                .size(20)
                .build();
        
        if (level != null && !level.isEmpty()) {
            try {
                searchRequest.setLevel(com.logstream.model.LogLevel.valueOf(level));
            } catch (IllegalArgumentException e) {
                // Invalid log level, ignore
            }
        }
        
        try {
            var logs = searchService.searchLogs(searchRequest);
            model.addAttribute("logs", logs);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", logs.getTotalPages());
        } catch (Exception e) {
            model.addAttribute("logs", java.util.Collections.emptyList());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
        }
        
        return "logs :: logsTable";
    }

    @PostMapping("/logs/import/csv")
    public String importLogsFromCsv(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            logImportService.initiateImport(file);
            redirectAttributes.addFlashAttribute("success", "Successfully imported log entries from file");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to import: " + e.getMessage());
        }
        return "redirect:/logs";
    }

    @GetMapping("/analytics")
    public String analytics(
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "hour") String granularity,
            Model model) {
        model.addAttribute("pageTitle", "Analytics");
        model.addAttribute("sidebarCollapsed", false);
        model.addAttribute("isAdmin", isAdmin());
        
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(7, ChronoUnit.DAYS);
        
        var errorRates = analyticsService.getErrorRatePerService();
        
        model.addAttribute("selectedService", service);
        model.addAttribute("errorRates", errorRates);
        model.addAttribute("commonErrors", analyticsService.getCommonErrors(service, 10, startTime, endTime));
        model.addAttribute("logVolume", analyticsService.getLogVolumeTimeSeries(service, granularity, startTime, endTime));
        model.addAttribute("services", errorRates);
        model.addAttribute("granularity", granularity);
        return "analytics";
    }

    @GetMapping("/users")
    public String userManagement(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        model.addAttribute("pageTitle", "User Management");
        model.addAttribute("sidebarCollapsed", false);
        model.addAttribute("isAdmin", isAdmin());
        
        try {
            var allUsers = authService.getAllUsers();
            
            int start = page * size;
            int end = Math.min(start + size, allUsers.size());
            var pagedUsers = start < allUsers.size() ? allUsers.subList(start, end) : java.util.Collections.emptyList();
            
            model.addAttribute("users", pagedUsers);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", (int) Math.ceil((double) allUsers.size() / size));
            model.addAttribute("totalUsers", allUsers.size());
        } catch (Exception e) {
            model.addAttribute("users", java.util.Collections.emptyList());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("totalUsers", 0);
            log.error("Failed to load users: {}", e.getMessage(), e);
        }
        
        return "users";
    }
}
