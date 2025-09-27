// src/main/java/com/uteq/SCLI/repository/AdminUsuarioRepository.java
package com.uteq.SCLI.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AdminUsuarioRepository {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    @Transactional
    public List<Object[]> listarUsuariosConRolDbRole() {
        // id_usuario, username, id_persona, id_rol, nombre_rol, db_role, activo
        String sql = """
            SELECT u.id_usuario,
                   u.username,
                   u.id_persona,
                   u.id_rol,
                   r.nombre_rol,
                   r.db_role,
                   u.activo
            FROM app.app_usuario u
            LEFT JOIN public.rol r ON r.id_rol = u.id_rol
            ORDER BY u.id_usuario
            """;
        Query q = em.createNativeQuery(sql);
        return q.getResultList();
    }

    @Transactional
    public Object[] crearUsuario(String username, String password, Integer idPersona, Integer idRol) {
        // fn_admin_crear_usuario(username text, password text, id_persona int, id_rol int)
        // retorna: (ok boolean, msg text, id_usuario int)
        Object r = em.createNativeQuery("SELECT * FROM app.fn_admin_crear_usuario(?,?,?,?)")
                .setParameter(1, username)
                .setParameter(2, password)
                .setParameter(3, idPersona)
                .setParameter(4, idRol)
                .getSingleResult();
        return (Object[]) r;
    }

    @Transactional
    public Object[] cambiarRol(Integer idUsuario, Integer idRol) {
        // fn_admin_cambiar_rol(id_usuario int, id_rol int) -> (ok boolean, msg text)
        Object r = em.createNativeQuery("SELECT * FROM app.fn_admin_cambiar_rol(?,?)")
                .setParameter(1, idUsuario)
                .setParameter(2, idRol)
                .getSingleResult();
        return (Object[]) r;
    }

    @Transactional
    public Object[] resetearClave(Integer idUsuario, String password) {
        // fn_admin_resetear_clave(id_usuario int, password text) -> (ok boolean, msg text)
        Object r = em.createNativeQuery("SELECT * FROM app.fn_admin_resetear_clave(?,?)")
                .setParameter(1, idUsuario)
                .setParameter(2, password)
                .getSingleResult();
        return (Object[]) r;
    }

     @Transactional
public Object[] obtenerPersonaCorreoNombre(Integer idPersona) {
    Object r = em.createNativeQuery("""
        SELECT correo,
               trim(coalesce(nombres,'') || ' ' || coalesce(apellidos,'')) AS nombre_completo
        FROM public.persona
        WHERE id_persona = ?
    """).setParameter(1, idPersona).getSingleResult();
    return (Object[]) r; // [correo, nombre_completo]
}

    @Transactional
public String obtenerNombreRol(Integer idRol) {
    Object r = em.createNativeQuery("""
        SELECT nombre_rol FROM public.rol WHERE id_rol = ?
    """).setParameter(1, idRol).getSingleResult();
    return r != null ? r.toString() : null;
}

    
}
