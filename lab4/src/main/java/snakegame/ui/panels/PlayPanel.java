package snakegame.ui.panels;

import io.reactivex.rxjava3.subjects.Subject;
import me.ippolitov.fit.snakes.SnakesProto;
import snakegame.snake.SnakeView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Function;

public class PlayPanel extends JPanel implements SnakeView {

    private final int canvasWidth = 1045;
    private final int canvasHeight = 810;

    // ===== НАСТРОЙКИ ПОЛЯ =====
    private int gridOffsetX = 19;
    private int gridOffsetY = 40;
    private int cellSize = 25;

    // ===== ГРАФИКА =====
    private Image backgroundImage;
    private Image appleImage;

    private Image greenHead;
    private Image greenBody;
    private Image redHead;
    private Image redBody;

    private SnakesProto.GameState state;

    public PlayPanel(Subject<Control> controlSubject) {
        setPreferredSize(new Dimension(canvasWidth, canvasHeight));
        setFocusable(true);

        backgroundImage = new ImageIcon("src/main/resources/snake_screen.png").getImage();
        appleImage = new ImageIcon("src/main/resources/apple.png").getImage();

        greenHead = new ImageIcon("src/main/resources/green_head.png").getImage();
        greenBody = new ImageIcon("src/main/resources/green_body.png").getImage();
        redHead   = new ImageIcon("src/main/resources/red_head.png").getImage();
        redBody   = new ImageIcon("src/main/resources/red_body.png").getImage();

        InputMap inputMap = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "up");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "down");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "left");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "right");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");

        Function<SnakesProto.Direction, AbstractAction> actionFactory =
                direction -> new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        controlSubject.onNext(new Control(null, direction));
                    }
                };

        actionMap.put("up", actionFactory.apply(SnakesProto.Direction.UP));
        actionMap.put("down", actionFactory.apply(SnakesProto.Direction.DOWN));
        actionMap.put("left", actionFactory.apply(SnakesProto.Direction.LEFT));
        actionMap.put("right", actionFactory.apply(SnakesProto.Direction.RIGHT));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        grabFocus();
        drawState((Graphics2D) g);
    }

    private void drawState(Graphics2D canvas) {
        if (state == null) return;

        canvas.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        if (backgroundImage != null) {
            canvas.drawImage(backgroundImage, 0, 0, canvasWidth, canvasHeight, null);
        }

        for (var snake : state.getSnakesList()) {

            boolean isGreen = snake.getPlayerId() % 2 == 0;

            Image headImg = isGreen ? greenHead : redHead;
            Image bodyImg = isGreen ? greenBody : redBody;

            boolean firstPoint = true;
            int x = 0, y = 0;

            for (var point : snake.getPointsList()) {
                int ax = x + point.getX();
                int ay = y + point.getY();

                int px = gridOffsetX + ax * cellSize;
                int py = gridOffsetY + ay * cellSize;

                Image img = firstPoint ? headImg : bodyImg;

                if (img != null) {
                    canvas.drawImage(img, px, py, cellSize, cellSize, null);
                } else {
                    canvas.setColor(isGreen ? Color.GREEN : Color.RED);
                    canvas.fillRect(px, py, cellSize, cellSize);
                }

                firstPoint = false;
                x = ax;
                y = ay;
            }
        }

        for (var food : state.getFoodsList()) {
            int px = gridOffsetX + food.getX() * cellSize;
            int py = gridOffsetY + food.getY() * cellSize;

            if (appleImage != null) {
                canvas.drawImage(appleImage, px, py, cellSize, cellSize, null);
            } else {
                canvas.setColor(Color.RED);
                canvas.fillRect(px, py, cellSize, cellSize);
            }
        }
    }

    @Override
    public void setState(SnakesProto.GameState state) {
        this.state = state;
        repaint();
    }

    private Image load(String path) {
        try {
            return new ImageIcon(path).getImage();
        } catch (Exception e) {
            System.err.println("Cannot load image: " + path);
            return null;
        }
    }
}
