package am.ik.categolj.app.dao;

import am.ik.categolj.app.domain.User;

public interface UserDao {
    User authUser(User user);

    User getUserById(Long id);

    User getUserByName(String name);

    void insertUser(User user);

    void updateUser(User user);

    void deleteUser(User user);
}