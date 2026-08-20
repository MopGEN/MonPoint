package dao;

import entidades.Proveedor;
import org.hibernate.Session;
import org.hibernate.Transaction;
import utils.HibernateUtil;

import java.util.List;

public class ProveedorDAOImpl implements ProveedorDAO {

    @Override
    public void guardar(Proveedor proveedor) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(proveedor);
            tx.commit();
        }
    }

    @Override
    public void actualizar(Proveedor proveedor) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(proveedor);
            tx.commit();
        }
    }

    @Override
    public void eliminar(Proveedor proveedor) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(proveedor);
            tx.commit();
        }
    }

    @Override
    public Proveedor buscarPorId(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Proveedor.class, id);
        }
    }

    @Override
    public List<Proveedor> listarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Proveedor", Proveedor.class).list();
        }
    }
}
