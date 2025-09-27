// src/main/java/com/uteq/SCLI/controller/AdminSesionesController.java
package com.uteq.SCLI.controller;

import com.uteq.SCLI.session.ActiveSession;
import com.uteq.SCLI.session.SessionTracker;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/dashboard/admin/sesiones")
public class AdminSesionesController {

  private final SessionTracker tracker;

  public AdminSesionesController(SessionTracker tracker) {
    this.tracker = tracker;
  }

  @GetMapping
  public String listado(Model model, HttpSession session) {
    // aquí puedes validar que el que entra sea admin (tu gate actual)
    model.addAttribute("items", tracker.listAll());
    return "dashboard/admin/sesiones"; // plantilla thymeleaf
  }

  @PostMapping("/kick")
  public String kick(@RequestParam String sessionId) {
    tracker.kick(sessionId);
    return "redirect:/dashboard/admin/sesiones?ok=1";
  }
}
