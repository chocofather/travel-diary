package com.example.travlediary.controller.board;

import com.example.travlediary.dto.BoardListDto;
import com.example.travlediary.service.board.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    // 1. 전체 페이지(SSR 최초 진입)
    @GetMapping("/board/list")
    public String boardListPage(
            @RequestParam(value = "boardType", required = false) String boardType,
            @RequestParam(value = "postType", required = false) String postType,
            @RequestParam(value = "sort", defaultValue = "latest") String sort,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = clampSize(size);
        List<BoardListDto> boardList = boardService.getBoardList(boardType, postType, sort, safePage, safeSize);
        int totalCount = boardService.getBoardCount(boardType, postType);
        int totalPages = (int) Math.ceil((double) totalCount / safeSize);

        model.addAttribute("boardList", boardList);
        model.addAttribute("boardType", boardType);
        model.addAttribute("postType", postType);
        model.addAttribute("sort", sort);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("pageTitle", "여행 커뮤니티");

        return "board/list";
    }

    // 2. fragment만 반환 (AJAX 페이징/정렬/검색 등)
    @GetMapping("/board/fragment")
    public String boardFragment(
            @RequestParam(value = "boardType", required = false) String boardType,
            @RequestParam(value = "postType", required = false) String postType,
            @RequestParam(value = "sort", defaultValue = "latest") String sort,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model
    ) {
        int safePage = Math.max(page, 1);
        int safeSize = clampSize(size);
        List<BoardListDto> boardList = boardService.getBoardList(boardType, postType, sort, safePage, safeSize);
        int totalCount = boardService.getBoardCount(boardType, postType);
        int totalPages = (int) Math.ceil((double) totalCount / safeSize);

        model.addAttribute("boardList", boardList);
        model.addAttribute("boardType", boardType);
        model.addAttribute("postType", postType);
        model.addAttribute("sort", sort);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", safeSize);

        return "board/fragment :: boardListFragment";
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
