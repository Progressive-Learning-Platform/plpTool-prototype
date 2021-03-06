package edu.asu.plp.user.dao.impl;

import edu.asu.plp.user.dao.UserDAO;
import edu.asu.plp.user.model.User;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/***
 * This class is to perform database operations on the User object
 */
public class JdbcUserDAO implements UserDAO {
    private DataSource dataSource;

    /***
     * @brief This function sets the data source
     * @param dataSource
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /***
     * @brief This function check if the user is already exist in the database. If the user is not present then
     * the user details will be inserted in the database
     * @param user
     * @return success if no exception occurs otherwise exception will be returned
     */
    public String saveUser(User user) {
        String sql = "INSERT INTO user_info " +
                "(email_id, first_name, last_name) VALUES (?, ?, ?)";

        String preCheck = "SELECT count(*) as rowcount from user_info WHERE email_id = ?";

        Connection conn = null;

        try {
            conn = dataSource.getConnection();
            PreparedStatement preCheckPS = conn.prepareStatement(preCheck);
            preCheckPS.setString(1, user.getEmail());
            ResultSet rs = preCheckPS.executeQuery();
            rs.next();
            int count = rs.getInt("rowcount");
            if (count == 1)
                return "success";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, user.getEmail());
            ps.setString(2, user.getFirstName());
            ps.setString(3, user.getLastName());
            ps.executeUpdate();
            ps.close();

        } catch (SQLException e) {
            return e.getMessage();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    return e.getMessage();
                }
            }
        }
        return "success";
    }

    /***
     * @brief This function checks if the database is connected or not
     * @return true of the database is connected otherwise false
     */
    @Override
    public boolean isDatabaseConnected() {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            if (conn != null) return true;

        } catch (SQLException e) {
            return false;
        }
        return true;
    }
}