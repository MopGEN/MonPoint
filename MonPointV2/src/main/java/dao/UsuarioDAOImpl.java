package dao;

import entidades.Usuario;
import org.hibernate.Session;
import org.hibernate.Transaction;
import utils.HibernateUtil;

import java.util.List;

public class UsuarioDAOImpl implements UsuarioDAO {

    @Override
    public void guardar(Usuario usuario) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(usuario);
            tx.commit();
        }
    }

    @Override
    public void actualizar(Usuario usuario) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(usuario);
            tx.commit();
        }
    }

    @Override
    public void eliminar(Usuario usuario) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(usuario);
            tx.commit();
        }
    }

    @Override
    public Usuario buscarPorId(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Usuario.class, id);
        }
    }

    @Override
    public Usuario buscarPorCorreo(String correo) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Usuario WHERE correo = :correo", Usuario.class)
                    .setParameter("correo", correo)
                    .uniqueResult();
        }
    }

    @Override
    public List<Usuario> listarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Usuario", Usuario.class).list();
        }
    }
}
