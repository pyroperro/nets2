package snakegame.ui;

import me.ippolitov.fit.snakes.SnakesProto;
import snakegame.io.PlayerController;
import snakegame.io.datatypes.MessageWithSender;
import snakegame.snake.SnakeView;
import snakegame.snake.SnakeViewController;
import snakegame.ui.panels.GamesPanel;
import snakegame.ui.panels.PlayerListPanel;
import snakegame.ui.panels.PlayPanel;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.BindException;

public class MainScreen {

    private PlayerController player;
    private SnakeView snakeView;

    private void joinGame(MessageWithSender gameMessage) {
        player.joinGame(gameMessage);
    }

    private void initUI() {
        JFrame frame = new JFrame("Snakes");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        ImageIcon icon = new ImageIcon("src/main/resources/snake.png");
        frame.setIconImage(icon.getImage());

        JPanel contents = new JPanel(new BorderLayout());

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        controlPanel.setPreferredSize(new Dimension(260, 0));

        JPanel playersPanel = new JPanel(new BorderLayout());
        playersPanel.setBorder(BorderFactory.createTitledBorder("Players"));
        playersPanel.add(
                new JScrollPane(new PlayerListPanel(player.getPlayersManager())),
                BorderLayout.CENTER
        );
        controlPanel.add(playersPanel);
        controlPanel.add(Box.createVerticalStrut(10));

        JPanel joinPanel = new JPanel(new BorderLayout());
        joinPanel.setBorder(BorderFactory.createTitledBorder("Join or create a game"));
        joinPanel.add(
                new JScrollPane(
                        new GamesPanel(
                                player.getAvailableGamesManager(),
                                this::joinGame
                        )
                ),
                BorderLayout.CENTER
        );

        JButton createButton = new JButton("Create game");
        createButton.addActionListener(e -> player.createGame());
        joinPanel.add(createButton, BorderLayout.SOUTH);

        controlPanel.add(joinPanel);

        contents.add(controlPanel, BorderLayout.EAST);

        contents.add((Component) snakeView, BorderLayout.CENTER);

        frame.setContentPane(contents);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        ((JComponent) snakeView).requestFocusInWindow();
    }

    public MainScreen() throws IOException {
        String userName = JOptionPane.showInputDialog("Enter your name:");

        int i;
        for (i = 0; i < 10; i++) {
            try {
                player = new PlayerController(
                        userName,
                        5000 + i,
                        SnakesProto.NodeRole.NORMAL
                );
                break;
            } catch (BindException ignored) {}
        }
        if (i == 10) {
            throw new RuntimeException("All ports are taken");
        }

        snakeView = new PlayPanel(player.getControlSubject());
        snakeView.setState(SnakesProto.GameState.getDefaultInstance());
        new SnakeViewController(player, snakeView);

        SwingUtilities.invokeLater(this::initUI);
    }

    public static void main(String[] args) throws IOException {
        new MainScreen();
    }
}

