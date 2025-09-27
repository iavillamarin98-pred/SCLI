package com.uteq.SCLI.service;

import com.uteq.SCLI.dto.AprobacionDTO;
import com.uteq.SCLI.dto.AprobacionItem;
import com.uteq.SCLI.repository.CoordinadorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminCoordService {

  private final CoordinadorRepository repo;

  // No la marcamos como final, ni la usamos en el @RequiredArgsConstructor
  @PersistenceContext
  private EntityManager em;

  // =================== Listado principal ===================
  public List<Map<String, Object>> listar(String estado) {
    var rows = repo.listarSolicitudesAdmin(estado == null ? null : estado.trim());
    var out = new ArrayList<Map<String, Object>>();
    for (Object[] r : rows) {
      Map<String, Object> m = new HashMap<>();
      m.put("id", ((Number) r[0]).intValue());
      m.put("fecha", String.valueOf(r[1]));
      m.put("estado", (String) r[2]);
      m.put("carrera", (String) r[3]);
      m.put("items", ((Number) r[4]).intValue());
      m.put("obs", (String) r[5]);
      out.add(m);
    }
    return out;
  }

  // =========== Labs disponibles para un horario ============
  @Transactional(readOnly = true)
  public List<Map<String, Object>> labsDisponibles(Integer idSolicitud, Integer idHorario) {
    Integer idCarrera = repo.carreraDeSolicitud(idSolicitud);
    Integer idPeriodo = repo.periodoActivoId();

    // Si el slot ya está ocupado en cualquier laboratorio del período, no hay disponibles
    if (Boolean.TRUE.equals(repo.slotOcupadoEnPeriodo(idHorario, idPeriodo))) {
      return List.of();
    }

    List<CoordinadorRepository.LabDisp> rows =
        repo.labsDisponibles(idCarrera, idHorario, idPeriodo);

    List<Map<String, Object>> out = new ArrayList<>();
    for (CoordinadorRepository.LabDisp r : rows) {
      Map<String, Object> m = new HashMap<>();
      m.put("id", r.getId());
      m.put("codigo", r.getCodigo());
      m.put("nombre", r.getNombre());
      m.put("preferido", Boolean.TRUE.equals(r.getPreferido()));
      out.add(m);
    }
    return out;
  }

  // ================== Detalle de la solicitud ==================
  @Transactional(readOnly = true)
  public List<Map<String, Object>> detalles(Integer idSolicitud) {
    var rows = repo.detallesSolicitud(idSolicitud);
    var out = new ArrayList<Map<String, Object>>();
    for (Object[] r : rows) {
      Map<String, Object> m = new HashMap<>();
      m.put("idDetalle", ((Number) r[0]).intValue());
      m.put("idHorario", ((Number) r[1]).intValue());
      m.put("materia", (String) r[2]);
      m.put("jornada", (String) r[3]);
      m.put("dia", (String) r[4]);
      m.put("hi", (String) r[5]);
      m.put("hf", (String) r[6]);
      out.add(m);
    }
    return out;
  }

  // ===================== Aprobar solicitud =====================
  @Transactional
  public Map<String, Object> aprobar(Integer idSolicitud, AprobacionDTO dto) {
    Integer periodoId = repo.periodoActivoId();
    if (periodoId == null) throw new IllegalStateException("No hay período activo.");

    // 1) Mapa "por horario" (prioridad 1)
    Map<Integer, Integer> labPorHorario = Optional.ofNullable(dto.getLabs())
        .orElse(List.of())
        .stream()
        .filter(it -> it.getIdHorario() != null && it.getIdLaboratorio() != null)
        .collect(Collectors.toMap(
            AprobacionItem::getIdHorario,
            AprobacionItem::getIdLaboratorio,
            (a, b) -> a
        ));

    // 2) Mapa "por materia" (prioridad 2). Clave = texto de materia que llega en detalles()
    Map<String, Integer> labPorMateria = Optional.ofNullable(dto.getLabsMateria())
        .orElseGet(HashMap::new);

    var det = repo.detallesSolicitud(idSolicitud);
    var sinLab = new ArrayList<Integer>();
    Integer idCarrera = repo.carreraDeSolicitud(idSolicitud);

    for (Object[] d : det) {
      Integer idHor = ((Number) d[1]).intValue();
      String materia = (String) d[2];
      Integer idDoc = null; // no viene docente en tu consulta actual

      // 🚨 NUEVO: si el hueco ya está ocupado en el período, no se puede asignar
      if (repo.slotOcupadoEnPeriodo(idHor, periodoId)) {
        sinLab.add(idHor);
        continue;
      }

      Integer elegido = labPorHorario.get(idHor); // prioridad 1: por horario

      // prioridad 2: por materia (si coincide y está disponible en ESTE horario)
      if (elegido == null && labPorMateria.containsKey(materia)) {
        Integer candidato = labPorMateria.get(materia);
        var disp = repo.labsDisponibles(idCarrera, idHor, periodoId);
        boolean ok = disp.stream().anyMatch(ld -> Objects.equals(ld.getId(), candidato));
        if (ok) elegido = candidato;
      }

      // prioridad 3: fallback al primer disponible
      if (elegido == null) {
        var lista = repo.labsDisponibles(idCarrera, idHor, periodoId);
        elegido = lista.isEmpty() ? null : lista.get(0).getId();
      }

      if (elegido == null) {
        sinLab.add(idHor);
        continue;
      }

      repo.insertarAsignacionConLaboratorio(elegido, idHor, materia, idDoc, periodoId);
    }

    String extra = sinLab.isEmpty()
        ? null
        : "No se pudo asignar " + sinLab.size() +
          " horario(s) porque ya están ocupados o no hay laboratorio disponible.";
    String obsFinal = (dto.getObservaciones() == null || dto.getObservaciones().isBlank())
        ? extra
        : (extra == null ? dto.getObservaciones() : dto.getObservaciones() + "\n" + extra);

    repo.actualizarEstado(
    idSolicitud,
    sinLab.isEmpty() ? "Aprobada" : "Propuesta",
    obsFinal
);

// Importante: aunque haya sinLab, devolvemos ok=true y marcamos parcial=true
Map<String, Object> resp = new HashMap<>();
resp.put("ok", true);
resp.put("parcial", !sinLab.isEmpty());
resp.put("sinLaboratorio", sinLab); // ids de horarios sin asignación
return resp;
  }

  // ======================= Rechazar =======================
  @Transactional
  public void rechazar(Integer idSolicitud, String motivo) {
    repo.actualizarEstado(idSolicitud, "Rechazada", motivo == null ? "" : motivo.trim());
  }

  // ======================= Proponer =======================
  @Transactional
  public Map<String, Object> proponer(Integer idSolicitud, String obs, List<Map<String, Object>> celdas) {
    repo.limpiarDetalles(idSolicitud);

    for (Map<String, Object> c : celdas) {
      Integer idHorario = Integer.parseInt(String.valueOf(c.get("idHorario")));
      Integer idMateria = Integer.parseInt(String.valueOf(c.get("idMateria")));
      Integer idDocente = (c.get("idDocente") == null || String.valueOf(c.get("idDocente")).isBlank())
          ? null
          : Integer.parseInt(String.valueOf(c.get("idDocente")));

      String mat = repo.materiaTexto(idMateria);
      String doc = (idDocente == null ? null : repo.docenteTexto(idDocente));
      String label = (doc == null ? mat : mat + " (" + doc + ")");
      repo.insertarDetalleAdmin(idSolicitud, idHorario, label);
    }

    repo.actualizarEstado(idSolicitud, "Propuesta", obs == null ? "" : obs.trim());
    return Map.of("ok", true, "idSolicitud", idSolicitud, "items", celdas.size());
  }
}
