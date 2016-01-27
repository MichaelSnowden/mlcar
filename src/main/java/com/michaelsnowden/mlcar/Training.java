package com.michaelsnowden.mlcar;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.michaelsnowden.mlcar.Constants.*;

/**
 * @author michael.snowden
 */
public class Training implements KeyListener, ActionListener, Runnable {
    double carX;
    double carY;

    private final JFrame frame;
    private final BufferedImage carImage;
    private final BufferedImage backgroundImage;
    private TurnDirection turn = TurnDirection.NONE;
    private double carAngle = Math.PI;

    public Training() throws IOException, SQLException, ClassNotFoundException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:test.sqlite");
        connection.setAutoCommit(true);
        final Statement statement = connection.createStatement();

        frame = new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);

        carImage = ImageIO.read(getClass().getClassLoader().getResource("car.png"));
        backgroundImage = ImageIO.read(getClass().getClassLoader().getResource("racetrack1.png"));
        frame.setSize(backgroundImage.getWidth(), backgroundImage.getHeight());
        carX = backgroundImage.getWidth() / 2.0;
        carY = backgroundImage.getHeight() / 6.0;

        statement.executeUpdate("DROP TABLE IF EXISTS readings");
        statement.executeUpdate(
                "CREATE TABLE readings (" +
                        "a REAL," +
                        "b REAL," +
                        "c REAL," +
                        "d REAL," +
                        "e REAL," +
                        "turningLeft INT," +
                        "turningRight INT," +
                        "notTurning INT)");
        final JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundImage, 0, 0, null);
                AffineTransform at = new AffineTransform();
                at.translate(carX + carImage.getWidth() / 2, carY + carImage.getHeight() / 2);
                at.rotate(carAngle);
                at.translate(-carImage.getWidth() / 2, -carImage.getHeight() / 2);
                Graphics2D g2d = (Graphics2D) g;
                g2d.drawImage(carImage, at, null);

                double centerX = carX + carImage.getWidth() / 2.0;
                double centerY = carY + carImage.getHeight() / 2.0;

                g.setColor(Color.GREEN);
                try {
                    int leftTurn = 0;
                    int rightTurn = 0;
                    int noTurn = 0;
                    if (turn.equals(TurnDirection.LEFT)) {
                        leftTurn = 1;
                    } else if (turn.equals(TurnDirection.RIGHT)) {
                        rightTurn = 1;
                    } else {
                        noTurn = 1;
                    }
                    double thetas[] = {Math.PI / 2, Math.PI / 4, 0, -Math.PI / 4, -Math.PI / 2};
                    List<Double> distances = new ArrayList<>();
                    for (double theta : thetas) {
                        double angle = carAngle + theta;
                        double distance = getDistance(angle, centerX, centerY);
                        distances.add(1 / distance);
                        g.drawLine(
                                (int) centerX,
                                (int) centerY,
                                (int) (Math.cos(angle) * distance + centerX),
                                (int) (Math.sin(angle) * distance + centerY));
                    }
                    statement.execute(
                            "INSERT INTO readings VALUES ("
                                    + distances.stream()
                                    .map(String::valueOf)
                                    .collect(Collectors.joining(", ")) + ","
                                    + leftTurn + ", "
                                    + rightTurn + ", "
                                    + noTurn + ")");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        };

        frame.add(panel);
        frame.addKeyListener(this);
    }

    private double getDistance(double theta, double centerX, double centerY) {
        double step = 0.0;
        double checkX, checkY;
        do {
            checkX = step * Math.cos(theta) + centerX;
            checkY = step * Math.sin(theta) + centerY;
            int clr = backgroundImage.getRGB((int) checkX, (int) checkY);
            if ((clr & 0x00ffffff) > 0) {
                return Math.sqrt(Math.pow(checkX - centerX, 2) + Math.pow(checkY - centerY, 2));
            }
            step += 0.1;
        }
        while (checkX >= 0 && (int) checkX < backgroundImage.getWidth() && checkY >= 0 && (int) checkY <
                backgroundImage.getHeight());
        throw new RuntimeException("No bounding wall found");
    }

    public void actionPerformed(ActionEvent e) {
        double deltaX = VELOCITY * Math.cos(carAngle);
        double deltaY = VELOCITY * Math.sin(carAngle);
        carX += deltaX;
        carY += deltaY;
        switch (turn) {
            case LEFT:
                carAngle -= DELTA_ANGLE;
                break;
            case RIGHT:
                carAngle += DELTA_ANGLE;
                break;
            case NONE:
                break;
        }
        frame.repaint();
    }

    public void run() {
        Timer timer = new Timer(10, this);
        timer.start();
        frame.setFocusable(true);
        frame.requestFocus();
    }

    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException {
        Training training = new Training();
        training.run();
    }

    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        switch (keyCode) {
            case KeyEvent.VK_LEFT:
                turn = TurnDirection.LEFT;
                break;
            case KeyEvent.VK_RIGHT:
                turn = TurnDirection.RIGHT;
                break;
        }
    }

    public void keyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        switch (keyCode) {
            case KeyEvent.VK_LEFT:
                if (turn == TurnDirection.LEFT) {
                    turn = TurnDirection.NONE;
                }
                break;
            case KeyEvent.VK_RIGHT:
                if (turn == TurnDirection.RIGHT) {
                    turn = TurnDirection.NONE;
                }
                break;
        }
    }

    public void keyTyped(KeyEvent e) {

    }
}
