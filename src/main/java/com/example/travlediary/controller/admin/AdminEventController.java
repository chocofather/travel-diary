package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.EventForm;
import com.example.travlediary.model.Event;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.event.EventService;
import com.example.travlediary.service.event.EventValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;

@RequiredArgsConstructor
@Controller
@RequestMapping("/admin/event")
public class AdminEventController {

    private static final String FORM_VIEW = "admin/event/event-form";
    private static final String LIST_VIEW = "admin/event/event-list";
    private static final String REDIRECT_LIST = "redirect:/admin/event/list";

    private final EventService eventService;

    @GetMapping("/new")
    public String newForm(Model model) {
        prepareFormModel(model, new EventForm(), null, null);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@ModelAttribute("eventForm") EventForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, form, null, null);
            return FORM_VIEW;
        }
        try {
            eventService.create(form, userDetails.getId());
        } catch (EventValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, null, null);
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Event event = eventService.getAdminEvent(id);
        prepareFormModel(model, EventForm.from(event), id, event);
        return FORM_VIEW;
    }

    @PostMapping("/{id:\\d+}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("eventForm") EventForm form,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            prepareFormModel(model, form, id, eventService.getAdminEvent(id));
            return FORM_VIEW;
        }
        try {
            eventService.update(id, form);
        } catch (EventValidationException exception) {
            rejectValidation(bindingResult, exception);
            prepareFormModel(model, form, id, eventService.getAdminEvent(id));
            return FORM_VIEW;
        }
        return REDIRECT_LIST;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        eventService.deleteEventById(id);
        return "redirect:/admin";
    }

    @GetMapping("/list")
    public String eventList(Model model) {
        model.addAttribute("eventList", eventService.selectAllEvents());
        model.addAttribute("today", LocalDate.now());
        return LIST_VIEW;
    }

    private void rejectValidation(BindingResult bindingResult,
                                  EventValidationException exception) {
        if (exception.getField() == null) {
            bindingResult.reject("event.invalid", exception.getMessage());
            return;
        }
        bindingResult.rejectValue(exception.getField(), "event.invalid", exception.getMessage());
    }

    private void prepareFormModel(Model model, EventForm form, Long id, Event current) {
        boolean editMode = id != null;
        model.addAttribute("eventForm", form);
        model.addAttribute("editMode", editMode);
        model.addAttribute("eventId", id);
        model.addAttribute("formAction", editMode
                ? "/admin/event/" + id + "/edit"
                : "/admin/event");
        model.addAttribute("pageTitle", editMode ? "이벤트 수정" : "이벤트 등록");
        model.addAttribute("pageDescription", editMode
                ? "이벤트 내용과 노출 기간, 이미지를 수정합니다."
                : "이벤트 내용과 노출 기간, 이미지를 등록합니다.");
        model.addAttribute("submitLabel", editMode ? "변경사항 저장" : "이벤트 등록");
        model.addAttribute("currentEventImage", current == null ? null : current.getEventImg());
        model.addAttribute("currentPosterImage", current == null ? null : current.getPosterImg());
    }
}
