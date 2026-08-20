package dao;

import entidades.Producto;
import org.hibernate.Session;
import org.hibernate.Transaction;
import utils.HibernateUtil;

import java.util.List;

public class ProductoDAOImpl implements ProductoDAO {

    @Override
    public void guardar(Producto producto) {
        if (producto.getId() > 0) {
            throw new IllegalArgumentException("El producto ya existe. Usa el método actualizar() en su lugar.");
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(producto);
            tx.commit();
        }
    }

    @Override
    public void actualizar(Producto producto) {
        if (producto.getId() <= 0) {
            throw new IllegalArgumentException("No se puede actualizar un producto sin ID válido.");
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(producto);
            tx.commit();
        }
    }

    @Override
    public void eliminar(Producto producto) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            producto.setActivo(false); // eliminación lógica
            session.merge(producto);
            tx.commit();
        }
    }

    @Override
    public Producto buscarPorId(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Producto.class, id);
        }
    }

    @Override
    public List<Producto> listarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Producto WHERE activo = true", Producto.class).list();
        }
    }
}
