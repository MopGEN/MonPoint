package enums;

public enum RolUsuario {
    ADMIN,
    VENDEDOR,
    OTRO;

    @Override
    public String toString() {
        return name().toLowerCase(); // para uso amigable visualmente
    }
}
