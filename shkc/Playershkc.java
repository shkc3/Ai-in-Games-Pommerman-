package players.shkc;

import core.GameState;
import players.Player;
import players.optimisers.ParameterizedPlayer;
import utils.ElapsedCpuTimer;
import utils.Types;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Random;

public class Playershkc extends ParameterizedPlayer {


    private Random Rn;


    public Types.ACTIONS[] actions;


    public Parametersshkc params;

    public Playershkc(long seed, int id) {
        this(seed, id, new Parametersshkc());
    }

    public Playershkc(long seed, int id, Parametersshkc params) {
        super(seed, id, params);
        reset(seed, id);

        ArrayList<Types.ACTIONS> actionsList = Types.ACTIONS.all();
        actions = new Types.ACTIONS[actionsList.size()];
        int i = 0;
        for (Types.ACTIONS act : actionsList) {
            actions[i++] = act;
        }
    }

    @Override
    public void reset(long seed, int playerID) {
        super.reset(seed, playerID);
        Rn = new Random(seed);

        this.params = (Parametersshkc) getParameters();
        if (this.params == null) {
            this.params = new Parametersshkc();
            super.setParameters(this.params);
        }
    }

    @Override
    public Types.ACTIONS act(GameState GS) {

        // TODO update GS
        if (GS.getGameMode().equals(Types.GAME_MODE.TEAM_RADIO)){
            int[] message = GS.getMessage();
        }

        ElapsedCpuTimer elapsedctimer = new ElapsedCpuTimer();
        elapsedctimer.setMaxTimeMillis(params.num_time);

        // Number of actions available
        int num_actions = actions.length;

        // Root of the tree
        SingleTreeNodeshkc n_root = new SingleTreeNodeshkc(params, Rn, num_actions, actions);
        n_root.setRootGameState(GS);

        //Determine the action using MCTS...
        n_root.MCTSSearch(elapsedctimer);

        //Determine the best action to take and return it.
        int action = n_root.mostVisitedAction();

        // TODO update message memory

        //... and return it.
        return actions[action];
    }

    @Override
    public int[] getMessage() {
        // default message
        int[] message = new int[Types.MESSAGE_LENGTH];
        message[0] = 1;
        return message;
    }

    @Override
    public Player copy() {
        return new Playershkc(seed, playerID, params);
    }
}