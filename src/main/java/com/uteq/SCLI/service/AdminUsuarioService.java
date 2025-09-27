// src/main/java/com/uteq/SCLI/service/AdminUsuarioService.java
package com.uteq.SCLI.service;

import com.uteq.SCLI.exception.EmailWarningException;
import com.uteq.SCLI.repository.AdminUsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUsuarioService {

    private static final Logger log = LoggerFactory.getLogger(AdminUsuarioService.class);

    private final AdminUsuarioRepository repo;
    private final EmailService emailService; 

    @Transactional
    public List<Object[]> listarUsuariosConRolDbRole() {
        return repo.listarUsuariosConRolDbRole();
    }

       @Transactional
    public void crearUsuario(String username, String password, Integer idPersona, Integer idRol) {
        Object[] r = repo.crearUsuario(username, password, idPersona, idRol);
        validarOk(r, "No se pudo crear el usuario");

        // En este punto el usuario YA está creado en BD
        try {
            Object[] p = repo.obtenerPersonaCorreoNombre(idPersona);
            String correo = p != null && p[0] != null ? p[0].toString() : null;
            String nombre = p != null && p[1] != null ? p[1].toString() : null;
            String rolNombre = repo.obtenerNombreRol(idRol);

            if (correo == null || correo.isBlank()) {
                // No romper flujo si la persona no tiene correo
                return;
            }
            // Envía la contraseña tal como la digitó el admin
            emailService.enviarCredenciales(correo, nombre, username, password, rolNombre);

        } catch (Exception e) {
            // No revertir creación por fallo de email
            throw new EmailWarningException("Usuario creado, pero falló el envío de correo.", e);
        }
    }

    @Transactional
    public void cambiarRol(Integer idUsuario, Integer idRol) {
        Object[] r = repo.cambiarRol(idUsuario, idRol);
        validarOk(r, "No se pudo cambiar el rol");
    }

    @Transactional
    public void resetearClave(Integer idUsuario, String password) {
        Object[] r = repo.resetearClave(idUsuario, password);
        validarOk(r, "No se pudo resetear la clave");
    }

    private void validarOk(Object[] r, String fallbackMsg) {
        if (r == null || r.length < 2) {
            throw new IllegalArgumentException(fallbackMsg + " (respuesta vacía)");
        }
        Boolean ok = toBool(r[0]);
        String msg = (r[1] != null) ? r[1].toString() : fallbackMsg;
        if (ok == null || !ok) {
            throw new IllegalArgumentException(msg);
        }
    }

    private Boolean toBool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        return Boolean.valueOf(o.toString());
    }



}
