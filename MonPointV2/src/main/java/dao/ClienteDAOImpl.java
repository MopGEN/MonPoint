package dao;

import entidades.Cliente;
import org.hibernate.Session;
import org.hibernate.Transaction;
import utils.HibernateUtil;

import java.util.List;

public class ClienteDAOImpl implements ClienteDAO {

    @Override
    public void guardar(Cliente cliente) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(cliente);
            tx.commit();
        }
    }

    @Override
    public void actualizar(Cliente cliente) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(cliente);
            tx.commit();
        }
    }

    @Override
    public void eliminar(Cliente cliente) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(cliente);
            tx.commit();
        }
    }

    @Override
    public Cliente buscarPorId(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Cliente.class, id);
        }
    }

    @Override
    public List<Cliente> listarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Cliente", Cliente.class).list();
        }
    }
}
