package com.logstream.controller;

import com.logstream.model.RetentionPolicy;
import com.logstream.service.AnalyticsService;
import com.logstream.service.HealthService;
import com.logstream.service.RetentionService;
import com.logstream.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final AnalyticsService analyticsService;
    private final HealthService healthService;
    private final RetentionService retentionService;
    private final SearchService searchService;

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("totalServices", healthService.getAllServiceHealth().size());
        model.addAttribute("healthyCount", healthService.getAllServiceHealth().stream()
                .filter(h -> "GREEN".equals(h.getStatus().toString())).count());
        model.addAttribute("degradedCount", healthService.getAllServiceHealth().stream()
                .filter(h -> "YELLOW".equals(h.getStatus().toString())).count());
        model.addAttribute("unhealthyCount", healthService.getAllServiceHealth().stream()
                .filter(h -> "RED".equals(h.getStatus().toString())).count());
        return "dashboard";
    }

    @GetMapping("/retention")
    public String retentionPolicies(Model model) {
        model.addAttribute("policies", retentionService.getAllPolicies());
        model.addAttribute("newPolicy", new RetentionPolicy());
        return "retention";
    }

    @PostMapping("/retention/add")
    public String addRetentionPolicy(@ModelAttribute RetentionPolicy policy, RedirectAttributes redirectAttributes) {
        retentionService.createPolicy(policy);
        redirectAttributes.addFlashAttribute("success", "Retention policy created successfully");
        return "redirect:/retention";
    }

    @GetMapping("/retention/edit/{id}")
    public String editRetentionPolicy(@PathVariable Long id, Model model) {
        model.addAttribute("policy", retentionService.getPolicyById(id));
        model.addAttribute("policies", retentionService.getAllPolicies());
        return "edit-retention";
    }

    @PostMapping("/retention/update/{id}")
    public String updateRetentionPolicy(@PathVariable Long id, @ModelAttribute RetentionPolicy policy, RedirectAttributes redirectAttributes) {
        retentionService.updatePolicy(id, policy);
        redirectAttributes.addFlashAttribute("success", "Retention policy updated successfully");
        return "redirect:/retention";
    }

    @GetMapping("/retention/delete/{id}")
    public String deleteRetentionPolicy(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        retentionService.deletePolicy(id);
        redirectAttributes.addFlashAttribute("success", "Retention policy deleted successfully");
        return "redirect:/retention";
    }

    @GetMapping("/logs")
    public String logManagement(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String search,
            Model model) {
        Pageable pageable = PageRequest.of(page, 20);
        var logs = searchService.searchLogs(service, level, search, pageable);
        model.addAttribute("logs", logs);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", logs.getTotalPages());
        model.addAttribute("services", analyticsService.getErrorRateByService());
        return "logs";
    }

    @GetMapping("/logs/export/csv")
    public String exportLogsToCsv(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String level,
            Model model) {
        var logs = searchService.searchLogs(service, level, null, Pageable.unpaged());
        model.addAttribute("logs", logs);
        return "csv-export";
    }

    @GetMapping("/logs/import")
    public String importLogsForm() {
        return "import-logs";
    }

    @PostMapping("/logs/import/csv")
    public String importLogsFromCsv(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean skipHeader = true;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (skipHeader) {
                    skipHeader = false;
                    continue;
                }
                count++;
            }
            redirectAttributes.addFlashAttribute("success", "Imported " + count + " log entries successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to import CSV: " + e.getMessage());
        }
        return "redirect:/logs";
    }

    @GetMapping("/analytics")
    public String analytics(
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "hour") String granularity,
            Model model) {
        model.addAttribute("errorRates", analyticsService.getErrorRateByService());
        model.addAttribute("commonErrors", analyticsService.getCommonErrors(10));
        model.addAttribute("logVolume", analyticsService.getLogVolumeByTime(granularity));
        model.addAttribute("services", analyticsService.getErrorRateByService());
        model.addAttribute("granularity", granularity);
        return "analytics";
    }
}
