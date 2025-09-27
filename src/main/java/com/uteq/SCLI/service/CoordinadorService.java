// src/main/java/com/uteq/SCLI/service/CoordinadorService.java
package com.uteq.SCLI.service;

import com.uteq.SCLI.dto.UserSession;
import com.uteq.SCLI.repository.CoordinadorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.uteq.SCLI.dto.coordinador.*;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CoordinadorService {

  private final CoordinadorRepository repo;
  private final UserSession userSession;
  @PersistenceContext private final EntityManager em;

  // Catálogos
  public List<Object[]> materiasPorCarrera(Integer idCarrera) {
    return repo.materiasPorCarrera(idCarrera);
  }

  public List<Object[]> docentesPorMateria(Integer idMateria) {
    return repo.docentesPorMateria(idMateria);
  }

  // ===== LISTAR =====
  public List<SolicitudItemDTO> listarSolicitudes(String estado) {
    ensureCoordinador();
    String filtro = (estado == null || estado.isBlank()) ? null : estado.trim();
    var rows = repo.listarSolicitudes(userSession.getIdPersona(), filtro);

    List<SolicitudItemDTO> out = new ArrayList<>();
    for (Object[] r : rows) {
      var x = new SolicitudItemDTO();
      x.idSolicitud = ((Number) r[0]).intValue();
      x.fecha       = ((java.sql.Date) r[1]).toLocalDate();
      x.estado      = (String) r[2];
      x.carrera     = (String) r[3];
      x.items       = ((Number) r[4]).intValue();
      x.observaciones = (String) r[5];
      out.add(x);
    }
    return out;
  }

  public List<Object[]> carrerasDelCoordinador(Integer idPersona) {
    return repo.carrerasMias(idPersona);
  }

  public List<Object[]> horariosPorJornada(String jornada) {
    return repo.horariosPorJornada(jornada);
  }

  // ===== DETALLES =====
  public List<SolicitudDetalleDTO> detallesSolicitud(Integer idSolicitud) {
    ensureCoordinador();
    if (!repo.esPropia(idSolicitud, userSession.getIdPersona()))
      throw new IllegalStateException("Solicitud no pertenece a este coordinador");

    var rows = repo.detallesSolicitud(idSolicitud);
    List<SolicitudDetalleDTO> out = new ArrayList<>();
    for (Object[] r : rows) {
      var d = new SolicitudDetalleDTO();
      d.idDetalle  = ((Number) r[0]).intValue();
      d.idHorario  = ((Number) r[1]).intValue();
      d.materia    = (String) r[2];
      d.jornada    = (String) r[3];
      d.diaSemana  = (String) r[4];
      d.horaInicio = (String) r[5];
      d.horaFin    = (String) r[6];
      out.add(d);
    }
    return out;
  }

  // ===== ANULAR =====
  @Transactional
  public boolean anular(Integer idSolicitud) {
    ensureCoordinador();
    if (!repo.esPropia(idSolicitud, userSession.getIdPersona()))
      throw new IllegalStateException("No autorizado para anular esta solicitud");

    int n = repo.anularSolicitud(idSolicitud);
    return n == 1;
  }

  // ===== Helper =====
  private void ensureCoordinador() {
    if (userSession == null || userSession.getNombreRol() == null ||
        !userSession.getNombreRol().equalsIgnoreCase("coordinador")) {
      throw new IllegalStateException("Rol no autorizado");
    }
  }

  // ===== CREAR SOLICITUD (con validación por período activo) =====
 // ===== CREAR SOLICITUD (inserta siempre los detalles; marca conflictos solo informativos) =====
@Transactional
public SolicitudCreadaDTO crearSolicitud(NuevaSolicitudDTO in) {
  // Seguridad mínima
  if (userSession == null || userSession.getNombreRol() == null ||
      !userSession.getNombreRol().equalsIgnoreCase("coordinador")) {
    throw new IllegalStateException("Rol no autorizado para crear solicitudes de coordinación");
  }

  // Cabecera
  Integer idSolicitud = repo.crearSolicitud(in.idCarrera, in.observaciones);
  var out = new SolicitudCreadaDTO();
  out.idSolicitud = idSolicitud;
  out.estado = "Pendiente";
  out.items = new ArrayList<>();

  if (in.celdas == null) return out;

  // Período activo (puede ser null si aún no existe)
  Integer periodoId = repo.periodoActivoId();

  for (var c : in.celdas) {
    var item = new SolicitudCreadaDTO.ItemResultado();
    item.idHorario = c.idHorario;

    // Armar texto de materia (con docente si viene)
    String materiaTxt = repo.materiaTexto(c.idMateria);
    if (materiaTxt == null) materiaTxt = "Materia " + c.idMateria;
    if (c.idDocente != null) {
      String docente = repo.docenteTexto(c.idDocente);
      if (docente != null && !docente.isBlank()) {
        materiaTxt = materiaTxt + " (" + docente + ")";
      }
    }
    item.materiaTexto = materiaTxt;

    // Validación informativa: ocupado SOLO si hay período activo
    boolean ocupado = (periodoId != null) && repo.slotOcupadoEnPeriodo(c.idHorario, periodoId);

    // 👉 Siempre insertamos el detalle para que el admin lo vea
    repo.insertarDetalle(idSolicitud, c.idHorario, materiaTxt);

    // Marcamos el resultado informativo
    if (ocupado) {
      item.conflictivo = true;
      item.motivo = "El horario ya está ocupado en el período activo.";
    } else {
      item.conflictivo = false;
      item.motivo = "OK";
    }

    out.items.add(item);
  }

  return out;
}

}
