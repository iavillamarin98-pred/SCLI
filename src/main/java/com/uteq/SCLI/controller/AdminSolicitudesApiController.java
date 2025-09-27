package com.uteq.SCLI.controller;

import com.uteq.SCLI.dto.AdminSolicitudItemDTO;
import com.uteq.SCLI.dto.LabOpcionDTO;
import com.uteq.SCLI.dto.HorarioOpcionDTO;
import com.uteq.SCLI.dto.UserSession;
import com.uteq.SCLI.service.AdminSolicitudesService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Slf4j
public class AdminSolicitudesApiController {

    private final AdminSolicitudesService service;
    private final UserSession userSession;

    // Listados
    @GetMapping("/solicitudes")
    public ResponseEntity<List<AdminSolicitudItemDTO>> listarAntigua(@RequestParam(required = false) String estado) {
        Integer idPersona = userSession.getIdPersona();
        return ResponseEntity.ok(service.listarParaAdmin(idPersona, estado));
    }

    @GetMapping("/solicitudes-docente")
    public ResponseEntity<List<AdminSolicitudItemDTO>> listarDocente(@RequestParam(required = false) String estado) {
        Integer idPersona = userSession.getIdPersona();
        return ResponseEntity.ok(service.listarParaAdmin(idPersona, estado));
    }

    // ======= ACCIONES =======
    @PostMapping("/solicitudes/{id}/aprobar")
    public ResponseEntity<Map<String, Object>> aprobar(@PathVariable("id") Integer idSolicitud,
                                                       @RequestBody AprobarReq body) {
        Integer idPersona = userSession.getIdPersona();
        Integer idLab = body == null ? null : body.getIdLaboratorio();
        log.info("POST /api/admin/solicitudes/{}/aprobar  persona={} lab={}", idSolicitud, idPersona, idLab);

        int updated = service.aprobar(idPersona, idSolicitud, idLab);
        log.info("… actualizaron {} fila(s)", updated);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/solicitudes/{id}/rechazar")
    public ResponseEntity<Map<String, Object>> rechazar(@PathVariable("id") Integer idSolicitud,
                                                        @RequestBody RechazarReq body) {
        Integer idPersona = userSession.getIdPersona();
        String motivo = body == null ? null : body.getMotivo();
        log.info("POST /api/admin/solicitudes/{}/rechazar  persona={} motivo='{}'", idSolicitud, idPersona, motivo);

        int updated = service.rechazar(idPersona, idSolicitud, motivo);
        log.info("… actualizaron {} fila(s)", updated);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/solicitudes/{id}/proponer")
    public ResponseEntity<Map<String, Object>> proponer(@PathVariable("id") Integer idSolicitud,
                                                        @RequestBody ProponerReq body) {
        Integer idPersona = userSession.getIdPersona();
        Integer idHorario = body == null ? null : body.getIdHorario();
        Integer idLaboratorio = body == null ? null : body.getIdLaboratorio();
        String mensaje = body == null ? null : body.getMensaje();
        log.info("POST /api/admin/solicitudes/{}/proponer  persona={} horario={} lab={} msg='{}'",
                idSolicitud, idPersona, idHorario, idLaboratorio, mensaje);

        int updated = service.proponer(idPersona, idSolicitud, idHorario, idLaboratorio, mensaje);
        log.info("… actualizaron {} fila(s)", updated);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    // ======= ENDPOINTS PARA MODALES (labs + horarios) =======
    @GetMapping("/solicitudes/{id}/labs")
    public ResponseEntity<List<LabOpcionDTO>> labs(@PathVariable("id") Integer idSolicitud) {
        return ResponseEntity.ok(service.labsParaSolicitud(idSolicitud));
    }

    @GetMapping("/solicitudes/{id}/horarios")
    public ResponseEntity<List<HorarioOpcionDTO>> horarios(@PathVariable("id") Integer idSolicitud,
                                                           @RequestParam(defaultValue = "Matutina") String jornada) {
        return ResponseEntity.ok(service.horariosParaSolicitud(idSolicitud, jornada));
    }

    // DTOs de request
    @Data public static class AprobarReq  { private Integer idLaboratorio; }
    @Data public static class RechazarReq { private String  motivo; }
    @Data public static class ProponerReq { private Integer idHorario; private Integer idLaboratorio; private String mensaje; }

    // ======= DEBUG =======
    @GetMapping("/solicitudes/{id}/debug")
    public Map<String, Object> debug(@PathVariable Integer id) {
        return Map.of(
            "count",  service.debugCount(id),
            "estado", service.debugEstado(id)
        );
    }
}
