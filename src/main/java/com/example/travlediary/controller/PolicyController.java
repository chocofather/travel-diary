package com.example.travlediary.controller;

import com.example.travlediary.model.PolicyType;
import com.example.travlediary.service.policy.SignupPolicy;
import com.example.travlediary.service.policy.SignupPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PolicyController {

    private final SignupPolicyService signupPolicyService;

    @GetMapping("/terms")
    public String terms(Model model) {
        return policyPage(model, PolicyType.TERMS_OF_SERVICE, "policy.terms.title");
    }

    @GetMapping("/privacy")
    public String privacy(Model model) {
        return policyPage(model, PolicyType.PRIVACY_POLICY, "policy.privacy.title");
    }

    private String policyPage(Model model, PolicyType type, String headingCode) {
        SignupPolicy policy = signupPolicyService.loadSignupPolicies().policies().stream()
                .filter(candidate -> candidate.type() == type)
                .findFirst()
                .orElse(null);
        model.addAttribute("policy", policy);
        model.addAttribute("policyHeadingCode", headingCode);
        return "policy/detail";
    }
}
