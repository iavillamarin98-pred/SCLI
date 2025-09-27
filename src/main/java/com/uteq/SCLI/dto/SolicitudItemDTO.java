package com.uteq.SCLI.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class SolicitudItemDTO {
    private Integer idSolicitud;
    private String  estado;
    private String  estadoRedireccion;
    private LocalDate fechaSolicitud;

    private Integer idHorario;
    private String  diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String  jornada;

    private String  materia;
    private String  tipoSolicitud;
}
