package com.example.version_control_system.controller;

import com.example.version_control_system.common.Result;
import com.example.version_control_system.dto.SearchHit;
import com.example.version_control_system.security.ProjectRole;
import com.example.version_control_system.security.RequireProjectRole;
import com.example.version_control_system.service.SearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 关键字搜索接口：成员可用。 */
@RestController
@RequestMapping("/api/projects/{projectId}/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @RequireProjectRole(ProjectRole.VIEWER)
    public Result<List<SearchHit>> search(@PathVariable("projectId") Long projectId,
                                          @RequestParam("keyword") String keyword) {
        return Result.success(searchService.search(projectId, keyword));
    }
}
