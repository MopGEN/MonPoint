package dao;

import entidades.DetalleVenta;
import org.hibernate.Session;
import org.hibernate.Transaction;
import utils.HibernateUtil;

import java.util.List;

public class DetalleVentaDAOImpl implements DetalleVentaDAO {

    @Override
    public void guardar(DetalleVenta detalle) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(detalle);
            tx.commit();
        }
    }

    @Override
    public void actualizar(DetalleVenta detalle) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(detalle);
            tx.commit();
        }
    }

    @Override
    public void eliminar(DetalleVenta detalle) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(detalle);
            tx.commit();
        }
    }

    @Override
    public DetalleVenta buscarPorId(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(DetalleVenta.class, id);
        }
    }

    @Override
    public List<DetalleVenta> listarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM DetalleVenta", DetalleVenta.class).list();
        }
    }
}
