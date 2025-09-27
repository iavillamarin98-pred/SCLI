// src/main/java/com/uteq/SCLI/service/AdminSolicitudesService.java
package com.uteq.SCLI.service;

import com.uteq.SCLI.dto.AdminSolicitudItemDTO;
import com.uteq.SCLI.dto.HorarioOpcionDTO;
import com.uteq.SCLI.dto.LabOpcionDTO;
import com.uteq.SCLI.repository.AdminSolicitudesRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminSolicitudesService {

    private final AdminSolicitudesRepository repo;

    public AdminSolicitudesService(AdminSolicitudesRepository repo) {
        this.repo = repo;
    }

    /* ================= Listados ================= */

    @Transactional(readOnly = true)
    public List<AdminSolicitudItemDTO> listarParaAdmin(Integer idPersonaAdmin, String estado) {
        Integer idAdminPiso = null;
        try {
            if (idPersonaAdmin != null) {
                idAdminPiso = repo.findIdAdminPisoByPersona(idPersonaAdmin);
            }
        } catch (Exception ignored) {}

        if (idAdminPiso != null) {
            return repo.listarPorPisoYEstado(idAdminPiso, estado);
        }
        return repo.listarPorEstadoGlobal(estado);
    }

    /* ================= Acciones ================= */

    @Transactional
    public int aprobar(Integer idPersonaAdmin, Integer idSolicitud, Integer idLaboratorio) {
        return repo.aprobarPorId(idSolicitud, idLaboratorio);
    }

    @Transactional
    public int rechazar(Integer idPersonaAdmin, Integer idSolicitud, String motivo) {
        return repo.rechazarPorId(idSolicitud, motivo == null ? "" : motivo);
    }

    @Transactional
    public int proponer(Integer idPersonaAdmin, Integer idSolicitud, Integer idHorarioAlt, Integer idLabAlt, String msg) {
        return repo.proponerPorId(idSolicitud, idHorarioAlt, idLabAlt, msg == null ? "" : msg);
    }

    /* ============ Datos para modales (labs / horarios) ============ */

    @Transactional(readOnly = true)
    public List<LabOpcionDTO> labsParaSolicitud(Integer idSolicitud) {
        return repo.listarLaboratoriosParaSolicitud(idSolicitud);
    }

    @Transactional(readOnly = true)
    public List<HorarioOpcionDTO> horariosParaSolicitud(Integer idSolicitud, String jornada) {
        return repo.listarHorariosParaSolicitud(idSolicitud, jornada);
    }

    /* ================= DEBUG (para el endpoint /debug) ================= */

    @Transactional(readOnly = true)
    public int debugCount(Integer id) {
        return repo.debugCount(id);
    }

    @Transactional(readOnly = true)
    public String debugEstado(Integer id) {
        return repo.debugEstado(id);
    }
}
