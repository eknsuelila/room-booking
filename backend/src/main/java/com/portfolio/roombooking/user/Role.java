package com.portfolio.roombooking.user;

/**
 * The two kinds of account in the system.
 *
 * <ul>
 *   <li>{@link #USER} - can browse rooms and manage <em>their own</em> bookings. Everyone who
 *       registers through the public sign-up form gets this role.</li>
 *   <li>{@link #ADMIN} - can additionally manage rooms, see/cancel everyone's bookings and view
 *       statistics. Admins are created by the seeder (or directly in the database), never via sign-up,
 *       so nobody can make themselves an admin.</li>
 * </ul>
 *
 * <p>Spring Security represents permissions as "authorities" (plain strings). By convention a
 * <em>role</em> is an authority prefixed with {@code ROLE_}: {@code hasRole('ADMIN')} in a
 * {@code @PreAuthorize} expression checks for the authority {@code ROLE_ADMIN}.
 * {@link #authority()} produces that string.
 *
 * <p>Stored in the {@code users.role} column as text ('ADMIN' / 'USER') via
 * {@code @Enumerated(EnumType.STRING)} - text is safer than the ordinal number because reordering
 * the enum constants later would otherwise silently change everyone's role.
 */
public enum Role {
    USER,
    ADMIN;

    /** The Spring Security authority name, e.g. {@code "ROLE_ADMIN"}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
