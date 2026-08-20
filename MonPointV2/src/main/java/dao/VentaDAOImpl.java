package dao;

import entidades.Venta;
import org.hibernate.Session;
import org.hibernate.Transaction;
import utils.HibernateUtil;

import java.time.LocalDate;
import java.util.List;

public class VentaDAOImpl implements VentaDAO {

    @Override
    public void guardar(Venta venta) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(venta);
            tx.commit();
        }
    }

    @Override
    public void actualizar(Venta venta) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(venta);
            tx.commit();
        }
    }

    @Override
    public void eliminar(Venta venta) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(venta);
            tx.commit();
        }
    }

    @Override
    public Venta buscarPorId(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Venta.class, id);
        }
    }

    @Override
    public List<Venta> listarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Venta", Venta.class).list();
        }
    }

    @Override
    public List<Venta> listarTodasVentas() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Venta v ORDER BY v.fecha DESC", Venta.class).list();
        }
    }

    @Override
    public List<Venta> listarVentasOrdenadas(String campo, boolean asc) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String orderDir = asc ? "ASC" : "DESC";
            String hql;
            if ("Cliente".equals(campo)) {
                hql = "SELECT v FROM Venta v JOIN v.cliente c ORDER BY c.nombre " + orderDir;
            } else {
                String prop = campo.toLowerCase();
                hql = "FROM Venta v ORDER BY v." + prop + " " + orderDir;
            }
            return session.createQuery(hql, Venta.class).list();
        }
    }

    @Override
    public List<Venta> listarVentasPorRangoFecha(LocalDate inicio, LocalDate fin) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                "FROM Venta v WHERE v.fecha BETWEEN :inicio AND :fin ORDER BY v.fecha DESC", Venta.class)
                .setParameter("inicio", inicio.atStartOfDay())
                .setParameter("fin", fin.atTime(23, 59, 59))
                .list();
        }
    }
}
