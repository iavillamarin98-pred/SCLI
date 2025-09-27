package com.uteq.SCLI.dto;

import lombok.Data;

@Data
public class CrearSolicitudRequest {
    private Integer idHorario;
    private String  materia;
    private String  tipoSolicitud; // "Nueva" | "Cambio"
    private Integer idAdminPiso;   // admin de piso a quien se enruta
}