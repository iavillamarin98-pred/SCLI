package com.uteq.SCLI.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    @GetMapping("/admin")
    public String admin() {
        // templates/dashboard/admin.html
        return "dashboard/admin";
    }

    @GetMapping("/docente")
    public String docente() {
        // templates/dashboard/docente.html
        return "dashboard/docente";
    }

    @GetMapping("/estudiante")
    public String estudiante() {
        // templates/dashboard/estudiante.html
        return "dashboard/estudiante";
    }

    @GetMapping("/docente/reservas")
    public String docenteReservas() {
        // busca templates/dashboard/docente/reservas.html
        return "dashboard/docente/reservas";
    }

    @GetMapping("/admin/solicitudes")
    public String adminSolicitudes() {
        // busca templates/dashboard/admin/solicitudes.html
        return "dashboard/admin/solicitudes";
    }
   
}
