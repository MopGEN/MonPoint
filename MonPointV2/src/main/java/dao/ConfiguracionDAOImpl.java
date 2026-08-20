package dao;

import entidades.Configuracion;
import org.hibernate.Session;
import org.hibernate.Transaction;
import utils.HibernateUtil;

public class ConfiguracionDAOImpl implements ConfiguracionDAO {

    @Override
    public void guardar(Configuracion configuracion) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = session.beginTransaction();
        session.persist(configuracion);
        tx.commit();
        session.close();
    }

    @Override
    public void actualizar(Configuracion configuracion) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = session.beginTransaction();
        session.merge(configuracion);
        tx.commit();
        session.close();
    }

    @Override
    public Configuracion obtener() {
        Session session = HibernateUtil.getSessionFactory().openSession();
        Configuracion config = session.createQuery("FROM Configuracion", Configuracion.class)
                                      .setMaxResults(1)
                                      .uniqueResult();
        session.close();
        return config;
    }
}
