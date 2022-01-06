package players.shkc;


import core.GameState;

import objects.Bomb;
import objects.Flame;
import objects.GameObject;

import players.heuristics.StateHeuristic;
import utils.Types;
import utils.Vector2d;

import java.lang.reflect.Type;
import java.util.*;
import static java.lang.Math.*;
import static java.lang.Math.abs;
import static utils.Utils.positionIsPassable;

public class Heuristicsshkc extends StateHeuristic {
    public Random Rn;
    public BoardStats rootStats;

    Heuristicsshkc(GameState GS, Random random) {
        this.Rn = random;
        this.rootStats = new BoardStats(GS, random);
    }

    @Override
    public double evaluateState(GameState GS) {
        boolean gameOver = GS.isTerminal();
        Types.RESULT win = GS.winner();

        BoardStats CurrBoard = new BoardStats(GS,Rn);
        double score = rootStats.score(CurrBoard);
        score = score/100;
        if(gameOver && win == Types.RESULT.LOSS)
            score = -1;

        if(gameOver && win == Types.RESULT.WIN)
            score = 1;

        return score;
    }

    public static class BoardStats {

        private Random Rn;
        private Types.TILETYPE[][] board;
        private ArrayList<Bomb> bombs;
        private ArrayList<Flame> flames;
        private ArrayList<GameObject> enemies;
        static double maximumWoods = -1;
        static double maximumBlastStrength = 10;
        private HashMap<Types.TILETYPE, ArrayList<Vector2d>> items;
        private HashMap<Vector2d, Integer> distance;
        private HashMap<Vector2d, Vector2d> prev;
        private double ammo;
        private double bStrength;//blastStrength
        private double numberOfWoods;
        boolean canKick;
        private Vector2d myPosition;

        BoardStats(GameState GS, Random random) {

            this.Rn = random;
            myPosition = GS.getPosition();
            ammo = GS.getAmmo();
            bStrength = GS.getBlastStrength();
            canKick = GS.canKick();
            this.numberOfWoods = 1;
            for (Types.TILETYPE[] gameObjectsTypes : GS.getBoard()) {
                for (Types.TILETYPE gameObjectType : gameObjectsTypes) {
                    if (gameObjectType == Types.TILETYPE.WOOD)
                        numberOfWoods++;
                }
            }
            if (maximumWoods == -1) {
                maximumWoods = numberOfWoods;
            }
            this.myPosition = GS.getPosition();
            this.board = GS.getBoard();
            int[][] bombBlastStrength = GS.getBombBlastStrength();
            int[][] bombLife = GS.getBombLife();
            int ammo = GS.getAmmo();
            int bStrength = GS.getBlastStrength();
            ArrayList<Types.TILETYPE> enemyIDs = GS.getAliveEnemyIDs();
            int boardSizeX = board.length;
            int boardSizeY = board[0].length;

            this.bombs = new ArrayList<>();
            this.enemies = new ArrayList<>();

            for (int a = 0; a < boardSizeX; a++) {
                for (int b = 0; b < boardSizeY; b++) {

                    if (board[b][a] == Types.TILETYPE.BOMB) {
                        // Creating a bomb object.
                        Bomb bomb = new Bomb();
                        bomb.setPosition(new Vector2d(a, b));
                        bomb.setBlastStrength(bombBlastStrength[b][a]);
                        bomb.setLife(bombLife[b][a]);
                        bombs.add(bomb);
                    }
                    else if (Types.TILETYPE.getAgentTypes().contains(board[b][a]) &&
                            board[b][a].getKey() != GS.getPlayerId()) { // May be an enemy
                        if (enemyIDs.contains(board[b][a])) { // Is enemy
                            // Create enemy object
                            GameObject enemy = new GameObject(board[b][a]);
                            enemy.setPosition(new Vector2d(a, b));
                            enemies.add(enemy); // no copy needed
                        }
                    }
                }
            }

            Container from_dijkstra = dijkstra(board, myPosition, bombs, enemies, 10);
            this.items = from_dijkstra.items;
//
            this.distance = from_dijkstra.distance;
//
            this.prev = from_dijkstra.prev;
//
        }

        public double score(BoardStats future) {

            int distance;
            for (Bomb b : future.bombs) {
                if (future.distance.containsKey(b.getPosition())) {
                    distance = future.distance.get(b.getPosition());
                    if (distance <= b.getBlastStrength()) {

                        return EvadeScoreFunction(future); // return evade policy

                    }
                }
            }
            ArrayList<Vector2d> floodAreaList = new ArrayList<>();
            for (GameObject obj : future.enemies) {
                if (future.distance.containsKey(obj.getPosition())) {
                    distance = future.distance.get(obj.getPosition());
                    if (distance <= 3) {
                        if(future.items.get(Types.TILETYPE.PASSAGE)==null){
                            return -10;
                        }
                        for (Vector2d V : future.items.get(Types.TILETYPE.PASSAGE)) {
                            if (future.distance.containsKey(V)) {
                                floodAreaList.add(V);
                            }
                        }
                        double SafeFactor = emptySafe(floodAreaList,future);
                        if(SafeFactor < 4){
                           return EvadeScoreFunction(future);
                        }
                        return AttackScoreFunction(future);
                        // return  the attack policy.
                    }
                }
            }

//            // else , return to explore policy/
            return ExploreScoreFunction(future);
        }

        public double EvadeScoreFunction(BoardStats future) {
            double score = 0;
            double count=1;
            double Pi;
            ArrayList<Bomb> new_b = new ArrayList<>();
            new_b = future.bombs;
            for (Bomb b : new_b) {
                if(future.distance.containsKey(b)) {
                    if (future.distance.get(b) <= b.getBlastStrength()) {
                        Pi = 1;
                    } else {
                        Pi = 0;
                    }
                    if (b.getLife() < 10) {
                        score +=  (11 - b.getLife());
                        count++;
                    }
                }
            }
            score = (25 * score) / (10*count);
            return 100 - score;
        }

        public double AttackScoreFunction(BoardStats future) {

            int count = 1, distance = 0;
            double score = 0;
            ArrayList<GameObject> e = future.enemies;
            for (GameObject a : e) {
                if(future.distance.containsKey(a)) {
                    distance = future.distance.get(a.getPosition());
                    if (distance <= 3) {
                        ArrayList<Vector2d> flood = floodFill(a, future);
//                        score = emptySafe(flood, future);
                        score = CalculateAttackScore(flood.size(),emptySafe(flood,future));
                        count++;
                    }
                }
            }
            score = score / count;
            return score;
        }

        private double emptySafe(ArrayList<Vector2d> floodArea, BoardStats future) {
            int floodAreaCount = floodArea.size();
            int depth;
            double kitna = floodAreaCount;
            ArrayList<Vector2d> placesToCheck = new ArrayList<>();
            for (Vector2d gali : floodArea) {
                for (Bomb phata : future.bombs) {
                    depth = phata.getBlastStrength()+1;
                    Vector2d bombpos = phata.getPosition();
                    placesToCheck.add(bombpos);
                    for (int i = 1; i < depth; i++) {
                        Vector2d d1 = new Vector2d(bombpos.x + i, bombpos.y);
                        Vector2d d2 = new Vector2d(bombpos.x - i, bombpos.y);
                        Vector2d d3 = new Vector2d(bombpos.x, bombpos.y - i);
                        Vector2d d4 = new Vector2d(bombpos.x, bombpos.y + i);
                        placesToCheck.add(d1);
                        placesToCheck.add(d2);
                        placesToCheck.add(d3);
                        placesToCheck.add(d4);
                    }
                }
                for (Vector2d aakhri : placesToCheck) {
                    if (aakhri == gali) {
                        kitna--;
                    }
                }
            }
            return kitna;
        }

        public double CalculateAttackScore(int floodAreaCount, double emptySafeArea) {
            double score = 100 * (1 - (emptySafeArea / floodAreaCount));
            return score;
        }

        public ArrayList<Vector2d> floodFill(GameObject enemy, BoardStats future) {
            int depth = 3;
            double value = 0;
            Vector2d enemyposition = enemy.getPosition();
            ArrayList<Vector2d> poslist = new ArrayList<>();
            ArrayList<GameObject> myenemies = future.enemies;
            int myID = future.board[enemyposition.y][enemyposition.x].getKey();
            GameObject newenemy = new GameObject(future.board[future.myPosition.y][future.myPosition.x]);
            newenemy.setPosition(new Vector2d(future.myPosition.x, future.myPosition.y));
            myenemies.add(newenemy);
            myenemies.remove(enemy);
            Container D_enemy = dijkstra(future.board, enemyposition, future.bombs, myenemies, depth);
            for (Vector2d V : D_enemy.items.get(Types.TILETYPE.PASSAGE)) {
                if (D_enemy.distance.containsKey(V)) {
                    poslist.add(V);
                }
            }
            return poslist;
        }

        public double ExploreScoreFunction(BoardStats futureState) {
            double diffWoods = - (futureState.numberOfWoods - this.numberOfWoods);
            int diffCanKick = futureState.canKick ? 1 : 0;
            double diffBlastStrength = futureState.bStrength - this.bStrength;
            double diffAmmo = futureState.ammo - this.ammo;
            ArrayList<Vector2d> floodAreaList = new ArrayList<>();
            if(futureState.items.get(Types.TILETYPE.PASSAGE)==null){
                return -10;
            }
            for (Vector2d V : futureState.items.get(Types.TILETYPE.PASSAGE)) {
                if (futureState.distance.containsKey(V)) {
                    floodAreaList.add(V);
                }
            }
            double SafeFactor = emptySafe(floodAreaList,futureState);
            SafeFactor = SafeFactor/floodAreaList.size();
            double score =  100 * (diffAmmo/4 + diffBlastStrength/maximumBlastStrength *0.2 + diffCanKick*0.3+ diffWoods/maximumWoods*0.3 + SafeFactor);

            return score;
        }

        /**
         * Dijkstra's pathfinding
         *
         * @param board      - game board
         * @param myPosition - the position of agent
         * @param bombs      - array of bombs in the game
         * @param enemies    - array of enemies in the game
         * @param depth      - depth of search (default: 10)
         * @return TODO
         */
        private Container dijkstra(Types.TILETYPE[][] board, Vector2d myPosition, ArrayList<Bomb> bombs,
                                   ArrayList<GameObject> enemies, int depth) {

            HashMap<Types.TILETYPE, ArrayList<Vector2d>> items = new HashMap<>();
            HashMap<Vector2d, Integer> distance = new HashMap<>();
            HashMap<Vector2d, Vector2d> prev = new HashMap<>();

            Queue<Vector2d> Q = new LinkedList<>();

            for (int r = max(0, myPosition.x - depth); r < min(board.length, myPosition.x + depth); r++) {
                for (int c = max(0, myPosition.y - depth); c < min(board.length, myPosition.y + depth); c++) {

                    Vector2d position = new Vector2d(r, c);

                    // Determines if two points are out of range of each other.
                    boolean out_of_range = (abs(c - myPosition.y) + abs(r - myPosition.x)) > depth;
                    if (out_of_range)
                        continue;

                    Types.TILETYPE itemType = board[r][c];
                    boolean positionInItems = (itemType == Types.TILETYPE.FOG ||
                            itemType == Types.TILETYPE.RIGID || itemType == Types.TILETYPE.FLAMES);
                    if (positionInItems)
                        continue;

                    ArrayList<Vector2d> itemsTempList = items.get(itemType);
                    if (itemsTempList == null) {
                        itemsTempList = new ArrayList<>();
                    }
                    itemsTempList.add(position);
                    items.put(itemType, itemsTempList);

                    if (position.equals(myPosition)) {
                        Q.add(position);
                        distance.put(position, 0);
                    } else {
                        distance.put(position, 100000); // TODO: Inf
                    }
                }
            }

            for (Bomb bomb : bombs) {
                if (bomb.getPosition().equals(myPosition)) {
                    ArrayList<Vector2d> itemsTempList = items.get(Types.TILETYPE.BOMB);
                    if (itemsTempList == null) {
                        itemsTempList = new ArrayList<>();
                    }
                    itemsTempList.add(myPosition);
                    items.put(Types.TILETYPE.BOMB, itemsTempList);
                }
            }

            while (!Q.isEmpty()) {
                Vector2d position = Q.remove();

                if (positionIsPassable(board, position, enemies)) {
                    int val = distance.get(position) + 1;

                    Types.DIRECTIONS[] directionsToBeChecked = Types.DIRECTIONS.values();

                    for (Types.DIRECTIONS directionToBeChecked : directionsToBeChecked) {

                        Vector2d direction = directionToBeChecked.toVec();
                        Vector2d new_position = new Vector2d(position.x + direction.x, position.y + direction.y);

                        if (!distance.containsKey(new_position))
                            continue;

                        int dist_val = distance.get(new_position);

                        if (val < dist_val) {
                            distance.put(new_position, val);
                            prev.put(new_position, position);
                            Q.add(new_position);
                        } else if (val == dist_val && Rn.nextFloat() < 0.5) {
                            distance.put(new_position, val);
                            prev.put(new_position, position);
                        }
                    }
                }
            }
            Container container = new Container();
            container.distance = distance;
            container.items = items;
            container.prev = prev;
            return container;
        }

        // Container for return values of Dijkstra's pathfinding algorithm.
        private class Container {
            HashMap<Types.TILETYPE, ArrayList<Vector2d>> items;
            HashMap<Vector2d, Integer> distance;
            HashMap<Vector2d, Vector2d> prev;

            Container() {   }
        }
    }
}