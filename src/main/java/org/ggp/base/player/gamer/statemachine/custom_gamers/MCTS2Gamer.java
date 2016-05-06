package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.lang.Math;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.StateMachine;

/**
 * MCTSGamer
 */
public final class MCTS2Gamer extends SampleGamer
{
    class TreeNode {
        private MachineState state;
        private List<TreeNode> children;
        private int visits;
        private TreeNode parent;
        private double utility;

        // constructor
        public TreeNode(MachineState state, TreeNode parent) {
          this.state = state;
          this.visits = 0;
          this.utility = 0.0;
          this.children = new ArrayList<TreeNode>();
          this.parent = parent;
        }

        // getter
        public MachineState getState() { return state; }
        public List<TreeNode> getChildren() { return children; }
        public TreeNode getParent() { return parent; }
        public int getVisits() { return visits; }
        public double getUtility() { return utility; }

        // setter
        public void incrementVisits() { this.visits++; }
        public void setUtility(double val) { this.utility = val; }
        public void addChild(TreeNode child) { 
            this.children.add(child); 
        }

        // overrides 
        @Override
        public boolean equals(Object object) {
            return this.state.equals(((TreeNode)object).state);
        }
        @Override
        public int hashCode() {
            return this.state.hashCode();
        }
    }

    // Hyperparameters - change based on gameplay
    int maxLevels = 1;
    int nProbes = 2;

    // Constants
    int upperThreshold = 100;
    int lowerThreshold = 0;
    long minPlayTimeLeft = PREFERRED_PLAY_BUFFER;
    long minMetagameTimeLeft = PREFERRED_METAGAME_BUFFER;

    // Game Type Enum - allows for special-casing, which good player like Sancho do
    public enum GameType {
        SINGLE_PLAYER_GAME,
        TWO_PLAYER_ALTERNATING_GAME,
        TWO_PLAYER_SIMULTANEOUS_GAME,
        MULTI_PLAYER_GAME
    }

    // TODO check for zero sum game

    // General Game Info Objects - set in metagame
    StateMachine sharedStateMachine = null;
    Role ourRole = null;
    Role otherRole = null; // for 2 player games only
    List<Role> roles = null;
    int numRoles = 0;
    GameType gameType; // describes type of game
    TreeNode currRootNode = null; 

    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        long start = System.currentTimeMillis(); 
        long metagameStopTime = timeout - minMetagameTimeLeft;

        // initialize private variables
        sharedStateMachine = getStateMachine();
        ourRole = getRole();
        roles = sharedStateMachine.getRoles();
        numRoles = roles.size();

        // get initial state
        MachineState initialState = sharedStateMachine.findInits();
        currRootNode = new TreeNode(initialState, null);

        // set game type
        if (numRoles == 1) {
            gameType = GameType.SINGLE_PLAYER_GAME;
        } else if (numRoles == 2) {
            List<Move> randomJointMove = sharedStateMachine.getRandomJointMove(initialState);
            if ((randomJointMove.get(0).toString().equals("noop") && !randomJointMove.get(1).toString().equals("noop")) 
                || (!randomJointMove.get(0).toString().equals("noop") && randomJointMove.get(1).toString().equals("noop"))) {
                gameType = GameType.TWO_PLAYER_ALTERNATING_GAME;
            } else {
                gameType = GameType.TWO_PLAYER_SIMULTANEOUS_GAME;
            }
            for (Role role : roles) {
                if (!role.equals(ourRole)) {
                    otherRole = role;
                    break;
                }
            }
        } else {
            gameType = GameType.MULTI_PLAYER_GAME;
        }
        
        // expand MCTS tree if we still have time
        while (System.currentTimeMillis() < metagameStopTime) {
            MCTS(currRootNode, metagameStopTime);
            System.out.println(currRootNode.getUtility() / currRootNode.getVisits());
        }
    }

    private int MCTS(TreeNode node, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        TreeNode selectedNode = selection(node);

        if (System.currentTimeMillis() > timeout) return -1;

        expansion(selectedNode);

        if (System.currentTimeMillis() > timeout) return -1;

        return performSimulation(selectedNode);
    }

    private TreeNode selection(TreeNode node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if (node.getVisits() == 0 || sharedStateMachine.findTerminalp(node.getState())) {
            return node;
        }
        for (int i = 0; i < node.getChildren().size(); i++) {
            if (node.getChildren().get(i).getVisits() == 0) {
                return node.getChildren().get(i);
            }
        }
        TreeNode selectedNode = node;
        int maxScore = -1;
        int minScore = 101;

        // for 2 player games - hacky
        List<Move> ourLegalMoves = null;
        List<Move> opponentLegalMoves = null;
        boolean isOurTurn = true;
        if (gameType == GameType.TWO_PLAYER_ALTERNATING_GAME) {
            ourLegalMoves = sharedStateMachine.findLegals(ourRole, node.state);
            opponentLegalMoves = sharedStateMachine.findLegals(otherRole, node.state);
            if (ourLegalMoves.size() == 1 && ourLegalMoves.get(0).toString().equals("noop")) {
                isOurTurn = false;
            }
            if (isOurTurn) {
                System.out.println("Our turn");
            } else {
                System.out.println("Their turn");
            }
        }

        for (int i = 0; i < node.getChildren().size(); i++) {
            int score = (int) selectfn(node.getChildren().get(i));
            if (gameType == GameType.SINGLE_PLAYER_GAME 
                || gameType == GameType.TWO_PLAYER_SIMULTANEOUS_GAME
                || gameType == GameType.MULTI_PLAYER_GAME) {
                if (score > maxScore) {
                    maxScore = score;
                    selectedNode = node.getChildren().get(i);
                }
            } else if (gameType == GameType.TWO_PLAYER_ALTERNATING_GAME) {
                // check if we are in a maximizing or minimizing node
                if (isOurTurn) {
                    if (score > maxScore) {
                        maxScore = score;
                        selectedNode = node.getChildren().get(i);
                    }
                } else {
                    // their turn (min node)
                    if (score < minScore) {
                        minScore = score;
                        selectedNode = node.getChildren().get(i);
                    }
                }
            }
        }
        return selection(selectedNode);
    }

    private double selectfn(TreeNode node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        return node.getUtility() + Math.sqrt(2 * Math.log(node.getParent().getVisits()/node.getVisits()));
    }

    private int expansion(TreeNode node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if (sharedStateMachine.findTerminalp(node.state)) {
            return -1;
        }
        List<MachineState> nextStates = sharedStateMachine.getNextStates(node.state);
        for (MachineState state : nextStates) {
            TreeNode nextNode = new TreeNode(state, node);
            node.addChild(nextNode);
        }
        return 0;
    }

    private int performSimulation(TreeNode node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        //somehow perform random simulation to get to a final state
        //int score = sharedStateMachine.findReward(ourRole, finalState);
        //backpropagation(finalState, score);
        //return score;
        return 0;
    }

    private void backpropagation(TreeNode node, int score) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        node.incrementVisits();
        node.setUtility(node.getUtility() + (double)score);
        if(node.getParent() != null) {
            backpropagation(node.getParent(), score);
        }
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        long start = System.currentTimeMillis();
        long stopTime = timeout - minPlayTimeLeft;
        List<Move> moves = getStateMachine().findLegals(getRole(), getCurrentState());
        Move selection = moves.get(0);

        while (System.currentTimeMillis() < minPlayTimeLeft) {
            MCTS(currRootNode, stopTime);
        }

        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
    }

    private Move bestMove(Role role, MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException 
    {
        List<Move> moves = getStateMachine().findLegals(role, state);
        Move best = moves.get(0);
        int score = 0;
        int alpha = -upperThreshold;
        int beta = upperThreshold + 1;
        for(int i = 0; i < moves.size(); i++) {
            int result = 0;
            if(result > score) {
                score = result;
                best = moves.get(i);
            }
        }
        return best;
    }

    /* 
     * TODO(Adi):
     * a) Need to handle multi-player case
     *      1. max/min nodes must be differentiated
     *      2. expansion should create bipartite tree between 
     *      max/min nodes
     * 
     * b) Need to construct tree during minimax routines
     */ 
}