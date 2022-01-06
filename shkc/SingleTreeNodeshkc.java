package players.shkc;

import core.GameState;
import players.heuristics.AdvancedHeuristic;
import players.heuristics.CustomHeuristic;
import players.heuristics.StateHeuristic;
import utils.ElapsedCpuTimer;
import utils.Types;
import utils.Utils;
import utils.Vector2d;

import java.util.ArrayList;
import java.util.Random;

public class SingleTreeNodeshkc{
    public Parametersshkc params;

    private SingleTreeNodeshkc parent;
    private SingleTreeNodeshkc[] children;
    private double totalValue;
    private double totalAMAFValue;
    private ArrayList<Double> samples;
    private int numVisits;
    private int numAMAFVisits;
    private Random Rn;
    private int m_depth;
    private double[] bounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};
    private double[] AMAFbounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};
    private int childIdx;
    private int fmCallsCount;
    public int c = -1;
    private int number_of_actions;
    private Types.ACTIONS[] actions;

    private GameState rootState;
    private StateHeuristic rootStateHeuristic;

    SingleTreeNodeshkc(Parametersshkc p, Random rnd, int number_of_actions, Types.ACTIONS[] actions) {
        this(p, null, -1, rnd, number_of_actions, actions, 0, null);
    }

    private SingleTreeNodeshkc(Parametersshkc p, SingleTreeNodeshkc parent, int childIdx, Random rnd, int number_of_actions,
                           Types.ACTIONS[] actions, int fmCallsCount, StateHeuristic sh) {
        this.params = p;
        this.fmCallsCount = fmCallsCount;
        this.parent = parent;
        this.Rn = rnd;
        this.number_of_actions = number_of_actions;
        this.actions = actions;
        children = new SingleTreeNodeshkc[number_of_actions];
        samples = new ArrayList<>();
        totalValue = 0.0;
        this.childIdx = childIdx;
        if (parent != null) {
            m_depth = parent.m_depth + 1;
            this.rootStateHeuristic = sh;
        } else
            m_depth = 0;

    }

    void setRootGameState(GameState gs) {
        this.rootState = gs;
        if (params.heuristic_method == params.CUSTOM_HEURISTIC)
            this.rootStateHeuristic = new CustomHeuristic(gs);
        else if (params.heuristic_method == params.ADVANCED_HEURISTIC) // New method: combined heuristics
            this.rootStateHeuristic = new AdvancedHeuristic(gs, Rn);
        else if (params.heuristic_method == params.HEURISTICSshkc)
            this.rootStateHeuristic = new Heuristicsshkc(gs, Rn);
    }


    void MCTSSearch(ElapsedCpuTimer elapsedTimer) {

        double averageTimeTaken;
        double accumulateTimeTaken = 0;
        long remaining;
        int numberIters = 0;

        int remainingLimit = 5;
        boolean stop = false;

        while (!stop) {

            GameState state = rootState.copy();
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            SingleTreeNodeshkc select = treePolicies(state, numberIters);
            double delta = select.rollOut(state);
            backUp(select, delta);


            // stopping the condition.
            if (params.stop_type == params.STOP_TIME) {
                numberIters++;
                accumulateTimeTaken += (elapsedTimerIteration.elapsedMillis());
                averageTimeTaken = accumulateTimeTaken / numberIters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * averageTimeTaken || remaining <= remainingLimit;
            } else if (params.stop_type == params.STOP_ITERATIONS) {
                numberIters++;
                stop = numberIters >= params.num_iterations;
            } else if (params.stop_type == params.STOP_FMCALLS) {
                fmCallsCount += params.rollout_depth;
                stop = (fmCallsCount + params.rollout_depth) > params.num_fmcalls;
            }
        }
//        System.out.println(" ITERS " + numberIters);
    }

    private SingleTreeNodeshkc treePolicies(GameState state, int numIterations) {

        SingleTreeNodeshkc curr = this;

        while (!state.isTerminal() && curr.m_depth < params.rollout_depth) {
            if (curr.notFullyExpanded()) {
                return curr.expand(state);

            } else {
//                curr = curr.uct(state);
                  curr = curr.uct2Tuned(state);
//                curr = curr.uctBayesian(state);
//                curr = curr.AMAF(state);
//                curr = curr.alphaAMAF(state, params.AMAPAplha);


            }
        }

        return curr;
    }


    private SingleTreeNodeshkc expand(GameState state) {

        int bestAction = 0;
        double bestVal = -1;

        for (int i = 0; i < children.length; i++) {
            double x = Rn.nextDouble();
            if (x > bestVal && children[i] == null) {
                bestAction = i;
                bestVal = x;
            }
        }

        //Roll state process
        roll(state, actions[bestAction]);

        SingleTreeNodeshkc Tn = new SingleTreeNodeshkc(params, this, bestAction, this.Rn, number_of_actions, actions, fmCallsCount, rootStateHeuristic);
        children[bestAction] = Tn;
        return Tn;
    }

    private void roll(GameState gs, Types.ACTIONS act) {
        //Simple, all random first, then my position.
        int nPlayers = 4;
        Types.ACTIONS[] actionsAll = new Types.ACTIONS[4];
        int playerId = gs.getPlayerId() - Types.TILETYPE.AGENT0.getKey();

        for (int i = 0; i < nPlayers; ++i) {
            if (playerId == i) {
                actionsAll[i] = act;
            } else {
                int actionIdx = Rn.nextInt(gs.nActions());
                actionsAll[i] = Types.ACTIONS.all().get(actionIdx);
            }
        }

        gs.next(actionsAll);

    }

    private SingleTreeNodeshkc uct(GameState state) {
        SingleTreeNodeshkc select = null;
        double bestVal = -Double.MAX_VALUE;
        for (SingleTreeNodeshkc child : this.children) {
            double hvVal = child.totalValue;
            double childValue = hvVal / (child.numVisits + params.epsilon);

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue +
                    params.K * Math.sqrt(Math.log(this.numVisits + 1) / (child.numVisits + params.epsilon));

            uctValue = Utils.noise(uctValue, params.epsilon, this.Rn.nextDouble());     //break ties randomly

            // Break ties in unexpanded nodes ,if small sampleRandom number.
            if (uctValue > bestVal) {
                select = child;
                bestVal = uctValue;
            }
        }
        if (select == null) {
            throw new RuntimeException("Warn! Returning null: " + bestVal + " : " + this.children.length + " " +
                    +bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        roll(state, actions[select.childIdx]);

        return select;
    }

    private double sd(ArrayList<Double> samples, double meanValue) {
        // Step 1:
        double mean = meanValue;
        double temp = 0;
        for (int i = 0; i < samples.size(); i++) {
            Double val = samples.get(i);
            // Step 2:
            double squrDiffToMean = Math.pow(val - mean, 2);
            // Step 3:
            temp += squrDiffToMean;
        }

        // Step 4:
        double meanOfDiffs = (double) temp / (double) (samples.size());

        // Step 5:
        return Math.sqrt(meanOfDiffs);
    }

    private SingleTreeNodeshkc uct2Tuned(GameState state) {
        SingleTreeNodeshkc select = null;
        double bestVal = -Double.MAX_VALUE;
        for (SingleTreeNodeshkc child : this.children) {
            double hvVal = child.totalValue;
            ArrayList<Double> childValues = child.samples;

            double childValue = hvVal / (child.numVisits + params.epsilon);
            double meanValue = hvVal / (child.numVisits);

            double variance = Math.pow(sd(childValues, meanValue), 2);
            double v_jn_j = variance +
                    params.K * Math.sqrt(Math.log(this.numVisits + 1) / (child.numVisits + params.epsilon));

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue +
                    Math.sqrt(Math.log(this.numVisits + 1) / (child.numVisits + params.epsilon) * Math.min(.25, v_jn_j));

            uctValue = Utils.noise(uctValue, params.epsilon, this.Rn.nextDouble());     //break ties randomly

            // Break ties in unexpanded nodes ,if small sampleRandom number.
            if (uctValue > bestVal) {
                select = child;
                bestVal = uctValue;
            }
        }
        if (select == null) {
            throw new RuntimeException("Warn! Returning null: " + bestVal + " : " + this.children.length + " " +
                    +bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        roll(state, actions[select.childIdx]);

        return select;
    }

    public static double sumArrayList(ArrayList<Double> li) {
        Double total = 0.0;
        Double avg = 0.0;
        for (int i = 0; i < li.size(); i++)
            total += li.get(i);
        avg = total / li.size();
        return avg;
    }



    private SingleTreeNodeshkc uctBayesian(GameState state) {
        SingleTreeNodeshkc select = null;
        double bestVal = -Double.MAX_VALUE;
        for (SingleTreeNodeshkc child : this.children) {
            double hvVal = child.totalValue;
            ArrayList<Double> childValues = child.samples;
            double childValue = hvVal / (child.numVisits + params.epsilon);
            double meanValue = hvVal / (child.numVisits);

            double variance = Math.pow(sd(childValues, meanValue), 2);
            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue + (
                    params.K * Math.sqrt(Math.log(this.numVisits + 1) / (child.numVisits + params.epsilon))) * variance;

            uctValue = Utils.noise(uctValue, params.epsilon, this.Rn.nextDouble());     //break ties randomly

            // small sampleRandom numbers: break ties in unexpanded nodes
            if (uctValue > bestVal) {
                select = child;
                bestVal = uctValue;
            }
        }
        if (select == null) {
            throw new RuntimeException("Warn! returning null: " + bestVal + " : " + this.children.length + " " +
                    +bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        roll(state, actions[select.childIdx]);

        return select;
    }

    private SingleTreeNodeshkc AMAF(GameState state) {
        SingleTreeNodeshkc select = null;
        double bestVal = -Double.MAX_VALUE;
        for (SingleTreeNodeshkc child : this.children) {
            double hvVal = child.totalValue;
            double childValue = hvVal / (child.numVisits + params.epsilon);

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue +
                    params.K * Math.sqrt(Math.log(this.numVisits + 1) / (child.numVisits + params.epsilon));

            uctValue = Utils.noise(uctValue, params.epsilon, this.Rn.nextDouble());     //break ties randomly

            double AMAFhvVal = child.totalAMAFValue;
            double AMAFchildValue = AMAFhvVal / (child.numAMAFVisits + params.epsilon);

            AMAFchildValue = Utils.normalise(AMAFchildValue, AMAFbounds[0], AMAFbounds[1]);

            double AMAFValue = AMAFchildValue +
                    params.K * Math.sqrt(Math.log(this.numAMAFVisits + 1) / (child.numAMAFVisits + params.epsilon));

            AMAFValue = Utils.noise(AMAFValue, params.epsilon, this.Rn.nextDouble());     //break ties randomly

            // Break ties in unexpanded nodes ,if small sampleRandom number.
            if (AMAFValue > bestVal) {
                select = child;
                bestVal = AMAFValue;
            }
        }
        if (select == null) {
            throw new RuntimeException("Warn! returning null: " + bestVal + " : " + this.children.length + " " +
                    +bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        roll(state, actions[select.childIdx]);

        return select;
    }

    private SingleTreeNodeshkc alphaAMAF(GameState state, double alpha) {
        SingleTreeNodeshkc select = null;
        double bestVal = -Double.MAX_VALUE;
        for (SingleTreeNodeshkc child : this.children) {
            double hvVal = child.totalValue;
            double childValue = hvVal / (child.numVisits + params.epsilon);

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue +
                    params.K * Math.sqrt(Math.log(this.numVisits + 1) / (child.numVisits + params.epsilon));

            uctValue = Utils.noise(uctValue, params.epsilon, this.Rn.nextDouble());     //break ties randomly

            double AMAFhvVal = child.totalAMAFValue;
            double AMAFchildValue = AMAFhvVal / (child.numAMAFVisits + params.epsilon);

            AMAFchildValue = Utils.normalise(AMAFchildValue, AMAFbounds[0], AMAFbounds[1]);

            double AMAFValue = AMAFchildValue +
                    params.K * Math.sqrt(Math.log(this.numAMAFVisits + 1) / (child.numAMAFVisits + params.epsilon));

            AMAFValue = Utils.noise(AMAFValue, params.epsilon, this.Rn.nextDouble());     //break ties randomly

            double alphaAMAFValue = ((alpha * AMAFValue) + ((1 - alpha) * uctValue));
            alphaAMAFValue = Utils.noise(alphaAMAFValue, params.epsilon, this.Rn.nextDouble());

            // Break ties in unexpanded nodes ,if small sampleRandom number.
            if (alphaAMAFValue > bestVal) {
                select = child;
                bestVal = alphaAMAFValue;
            }
        }
        if (select == null) {
            throw new RuntimeException("Warn! returning null: " + bestVal + " : " + this.children.length + " " +
                    +bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        roll(state, actions[select.childIdx]);

        return select;
    }

    private double rollOut(GameState state) {
        int thisDepth = this.m_depth;

        while (!finishRollout(state, thisDepth)) {
            int action = safeRandomAction(state);
            roll(state, actions[action]);
            thisDepth++;
        }

        return rootStateHeuristic.evaluateState(state);
    }

    private int safeRandomAction(GameState state) {
        Types.TILETYPE[][] board = state.getBoard();
        ArrayList<Types.ACTIONS> actionsToTry = Types.ACTIONS.all();
        int width = board.length;
        int height = board[0].length;

        while (actionsToTry.size() > 0) {

            int nAction = Rn.nextInt(actionsToTry.size());
            Types.ACTIONS act = actionsToTry.get(nAction);
            Vector2d dir = act.getDirection().toVec();

            Vector2d pos = state.getPosition();
            int x = pos.x + dir.x;
            int y = pos.y + dir.y;

            if (x >= 0 && x < width && y >= 0 && y < height)
                if (board[y][x] != Types.TILETYPE.FLAMES)
                    return nAction;

            actionsToTry.remove(nAction);
        }

        //Uh oh...
        return Rn.nextInt(number_of_actions);
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean finishRollout(GameState rollerState, int depth) {
        if (depth >= params.rollout_depth)      //rollout end condition.
            return true;

        if (rollerState.isTerminal())               //end of game
            return true;

        return false;
    }

    private void backUp(SingleTreeNodeshkc node, double result) {
        SingleTreeNodeshkc n = node;
        while (n != null) {
            n.numVisits++;
            n.totalValue += result;
            n.samples.add(result);
            if (result < n.bounds[0]) {
                n.bounds[0] = result;
            }
            if (result > n.bounds[1]) {
                n.bounds[1] = result;
            }
            n = n.parent;
        }
    }

    private void backUpAMAF(SingleTreeNodeshkc node, double result) {
        SingleTreeNodeshkc n = node;
        SingleTreeNodeshkc n_parent;
        while (n != null) {
            n.numVisits++;
            n.numAMAFVisits++;
            n.totalAMAFValue = n.totalAMAFValue + result;
            n.totalValue = n.totalValue + result;
            n.samples.add(result);
            if (result < n.bounds[0] && result < n.AMAFbounds[0]) {
                n.bounds[0] = result;
                if (n.AMAFbounds[0] == Double.MAX_VALUE || n.AMAFbounds[0] == -Double.MAX_VALUE)
                    n.AMAFbounds[0] = result;
                else
                    n.AMAFbounds[0] = n.AMAFbounds[0] + result;
            }
            if (result > n.bounds[1] && result > AMAFbounds[1]) {
                n.bounds[1] = result;
                if (n.AMAFbounds[1] == Double.MAX_VALUE || n.AMAFbounds[1] == -Double.MAX_VALUE)
                    n.AMAFbounds[1] = result;
                else
                    n.AMAFbounds[1] = n.AMAFbounds[1] + result;
            }

            n_parent = n.parent;
            while (n_parent != null) {
                for (SingleTreeNodeshkc child : n_parent.children) {
                    if (child == n && child != node) {
                        child.numAMAFVisits++;
                        child.totalAMAFValue = child.totalAMAFValue + result;
                        if (result < child.AMAFbounds[0]) {
                            if (child.AMAFbounds[0] == Double.MAX_VALUE || child.AMAFbounds[0] == -Double.MAX_VALUE)
                                child.AMAFbounds[0] = result;
                            else
                                child.AMAFbounds[0] = child.AMAFbounds[0] + result;
                        }
                        if (result > child.AMAFbounds[1]) {
                            if (child.AMAFbounds[1] == Double.MAX_VALUE || child.AMAFbounds[1] == -Double.MAX_VALUE)
                                child.AMAFbounds[1] = result;
                            else
                                child.AMAFbounds[1] = child.AMAFbounds[1] + result;
                        }
                    }
                }
                n_parent = n_parent.parent;
            }
            n = n.parent;
        }
    }

    int mostVisitedAction() {
        int select = -1;
        double bestVal = -Double.MAX_VALUE;
        boolean allEqual = true;
        double first = -1;

        for (int i = 0; i < children.length; i++) {

            if (children[i] != null) {
                if (first == -1)
                    first = children[i].numVisits;
                else if (first != children[i].numVisits) {
                    allEqual = false;
                }

                double childValue = children[i].numVisits;
                childValue = Utils.noise(childValue, params.epsilon, this.Rn.nextDouble());     //break ties randomly
                if (childValue > bestVal) {
                    bestVal = childValue;
                    select = i;
                }
            }
        }

        if (select == -1) {
            select = 0;
        } else if (allEqual) {
            // if all are equal, then we will choose for the one with best Q.
            select = bestAction();
        }

        return select;
    }

    private int bestAction() {
        int select = -1;
        double bestVal = -Double.MAX_VALUE;

        for (int i = 0; i < children.length; i++) {

            if (children[i] != null) {
                double childValue = children[i].totalValue / (children[i].numVisits + params.epsilon);
                childValue = Utils.noise(childValue, params.epsilon, this.Rn.nextDouble());
                // break ties will be randomly
                if (childValue > bestVal) {
                    bestVal = childValue;
                    select = i;
                }
            }
        }

        if (select == -1) {
            System.out.println("Unexpected selection!");
            select = 0;
        }

        return select;
    }


    private boolean notFullyExpanded() {
        for (SingleTreeNodeshkc Tn : children) {
            if (Tn == null) {
                return true;
            }
        }
        return false;
    }
}
