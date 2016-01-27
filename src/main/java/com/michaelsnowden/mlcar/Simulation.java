package com.michaelsnowden.mlcar;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static com.michaelsnowden.mlcar.Constants.*;

/**
 * @author michael.snowden
 */
public class Simulation implements ActionListener, Runnable {
    private final String file = "racetrack3.png";
    private double[] left;
    private double[] right;
    private double[] none;
    double carX;

    double carY;
    private final JFrame frame;
    private final BufferedImage carImage;
    private final BufferedImage backgroundImage;
    private TurnDirection turn = TurnDirection.NONE;
    private double carAngle = Math.PI;

    public Simulation() throws IOException, SQLException, ClassNotFoundException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:test.sqlite");
        connection.setAutoCommit(true);
        Statement statement = connection.createStatement();

        frame = new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);

        carImage = ImageIO.read(getClass().getClassLoader().getResource("car.png"));
        backgroundImage = ImageIO.read(getClass().getClassLoader().getResource(file));
        frame.setSize(backgroundImage.getWidth(), backgroundImage.getHeight());
        carX = backgroundImage.getWidth() / 2.0;
        carY = backgroundImage.getHeight() / 6.0;
        OLSMultipleLinearRegression turningLeftRegression = new OLSMultipleLinearRegression();
        OLSMultipleLinearRegression turningRightRegression = new OLSMultipleLinearRegression();
        OLSMultipleLinearRegression notTurningRegression = new OLSMultipleLinearRegression();
        try {
            ResultSet query = statement.executeQuery("SELECT COUNT(*) AS count FROM readings");
            query.next();
            int fetchSize = query.getInt("count");
            ResultSet resultSet = statement.executeQuery("SELECT * FROM readings");
            double[][] x = new double[fetchSize][];
            double[] yl = new double[fetchSize];
            double[] yr = new double[fetchSize];
            double[] yn = new double[fetchSize];
            int i = 0;
            while (resultSet.next()) {
                double a = resultSet.getDouble("a");
                double b = resultSet.getDouble("b");
                double c = resultSet.getDouble("c");
                double d = resultSet.getDouble("d");
                double e = resultSet.getDouble("e");
                double l = resultSet.getInt("turningLeft");
                double r = resultSet.getInt("turningRight");
                double n = resultSet.getInt("notTurning");
                x[i] = new double[]{a, b, c, d, e};
                yl[i] = l;
                yr[i] = r;
                yn[i] = n;
                ++i;
            }
            turningLeftRegression.newSampleData(yl, x);
            turningRightRegression.newSampleData(yr, x);
            notTurningRegression.newSampleData(yn, x);
            left = turningLeftRegression.estimateRegressionParameters();
            right = turningRightRegression.estimateRegressionParameters();
            none = notTurningRegression.estimateRegressionParameters();
        } catch (SQLException e1) {
            e1.printStackTrace();
        }
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
                double thetas[] = {Math.PI / 2, Math.PI / 4, 0, -Math.PI / 4, -Math.PI / 2};
//                for (double theta : thetas) {
//                    double angle = carAngle + theta;
//                    double distance = getDistance(angle, centerX, centerY);
//                    g.drawLine(
//                            (int) centerX,
//                            (int) centerY,
//                            (int) (Math.cos(angle) * distance + centerX),
//                            (int) (Math.sin(angle) * distance + centerY));
//                }
            }
        };

        frame.add(panel);
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
        double thetas[] = {Math.PI / 2, Math.PI / 4, 0, -Math.PI / 4, -Math.PI / 2};
        List<Double> distances = new ArrayList<>();
        double centerX = carX + carImage.getWidth() / 2.0;
        double centerY = carY + carImage.getHeight() / 2.0;
        for (double theta : thetas) {
            double angle = carAngle + theta;
            double distance = 1 / getDistance(angle, centerX, centerY);
            distances.add(distance);
        }
        double leftPred = 0.0;
        double rightPred = 0.0;
        double nonePred = 0.0;
        for (int i = 0; i < distances.size(); i++) {
            Double distance = distances.get(i);
            leftPred += left[i] * distance;
            rightPred += right[i] * distance;
            nonePred += none[i] * distance;
        }
        leftPred = Math.abs(leftPred - 1);
        rightPred = Math.abs(rightPred - 1);
        nonePred = Math.abs(nonePred - 1);
        if (leftPred < rightPred && leftPred < nonePred) {
            turn = TurnDirection.LEFT;
        } else if (rightPred < leftPred && rightPred < nonePred) {
            turn = TurnDirection.RIGHT;
        } else {
            turn = TurnDirection.NONE;
        }
    }

    public void run() {
        Timer timer = new Timer(10, this);
        timer.start();
    }

    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException {
        Simulation simulation = new Simulation();
        simulation.run();
    }
}
