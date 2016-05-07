package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
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
        private double totalUtility;
        private boolean ourTurn;

        // constructor
        public TreeNode(MachineState state, TreeNode parent) {
          this.state = state;
          this.visits = 0;
          this.totalUtility = 0.0;
          this.children = new ArrayList<TreeNode>();
          this.parent = parent;
        }

        // getter
        public MachineState getState() { return state; }
        public List<TreeNode> getChildren() { return children; }
        public TreeNode getParent() { return parent; }
        public int getVisits() { return visits; }
        public double getUtility() {
            if (visits == 0) return 0;
            return totalUtility / visits; 
        }
        public boolean getOurTurn() { return ourTurn; }

        // setter
        public void incrementVisits() { this.visits++; }
        public void incrementUtility(double val) { this.totalUtility += val; }
        public void setParent(TreeNode parent) {this.parent = parent; }
        public void addChild(TreeNode child) { 
            this.children.add(child); 
        }
        public void setOurTurn(boolean ourTurn) { this.ourTurn = ourTurn; }

        // overrides 
        @Override
        public boolean equals(Object object) {
            return this.state.equals(((TreeNode)object).getState());
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
    int ourTurnIndex = -1;

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

        for (int i = 0; i < roles.size(); i++) {
            if (roles.get(i).equals(ourRole)) {
                ourTurnIndex = i;
                break;
            }
        }

        // get initial state
        MachineState initialState = sharedStateMachine.findInits();
        currRootNode = new TreeNode(initialState, null);

        // set game type
        if (numRoles == 1) {
            gameType = GameType.SINGLE_PLAYER_GAME;
            currRootNode.setOurTurn(true); // one player case, always 0
        } else if (numRoles == 2) {
            int otherIndex = 1;
            if (ourTurnIndex == 1) {
                otherIndex = 0;
            }
            List<Move> randomJointMove = sharedStateMachine.getRandomJointMove(initialState);
            if (randomJointMove.get(ourTurnIndex).toString().equals("noop") && !randomJointMove.get(otherIndex).toString().equals("noop")) {
                gameType = GameType.TWO_PLAYER_ALTERNATING_GAME;
                currRootNode.setOurTurn(false);
            } else if (!randomJointMove.get(ourTurnIndex).toString().equals("noop") && randomJointMove.get(otherIndex).toString().equals("noop")) {
                gameType = GameType.TWO_PLAYER_ALTERNATING_GAME;
                currRootNode.setOurTurn(true);
            }  else {
                gameType = GameType.TWO_PLAYER_SIMULTANEOUS_GAME;
                currRootNode.setOurTurn(true);
            }
        } else {
            gameType = GameType.MULTI_PLAYER_GAME;
            List<Move> randomJointMove = sharedStateMachine.getRandomJointMove(initialState);
            currRootNode.setOurTurn(!randomJointMove.get(ourTurnIndex).toString().equals("noop"));
        }
        
        // expand MCTS tree if we still have time
        int numSimulations = 0;
        while (System.currentTimeMillis() < metagameStopTime - 1000) {
            MCTS(currRootNode, metagameStopTime);
            numSimulations++;
        }
        System.out.println("Metagame simulations completed: " + numSimulations);
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        long start = System.currentTimeMillis();
        long stopTime = timeout - minPlayTimeLeft;
        MachineState currState = getCurrentState();
        List<Move> moves = sharedStateMachine.findLegals(getRole(), currState);
        Move selection = moves.get(0);

        // currRootNode will be a child of currRootnode whose state is equal to current state
        if (!currState.equals(sharedStateMachine.findInits())) {
            for (TreeNode child : currRootNode.getChildren()) {
                if (child.getState().equals(currState)) {
                    currRootNode = child;
                    currRootNode.setParent(null);
                    break;
                }
            }
        }

        Map<MachineState, Move> jointMoveMap = new HashMap<MachineState, Move>();
        List<List<Move>> jointMoves = sharedStateMachine.getLegalJointMoves(currState);
        for (List<Move> jointMove : jointMoves) {
            MachineState nextState = sharedStateMachine.getNextState(currState, jointMove);
            jointMoveMap.put(nextState, jointMove.get(ourTurnIndex));
        }

        TreeNode bestChild = null;
        int numDepthCharges = 0;
        while (System.currentTimeMillis() < stopTime) {
            TreeNode tempBestChild = MCTS(currRootNode, stopTime);
            if (tempBestChild != null) {
                numDepthCharges++;
                bestChild = tempBestChild;
            } else {
                break;
            }
        }
        System.out.println("Simulations completed: " + numDepthCharges);

        if (bestChild != null) {
            List<MachineState> nextStates = sharedStateMachine.getNextStates(currRootNode.getState());
            selection = jointMoveMap.get(bestChild.getState());
        }

        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
    }

    // returns the best next node from the current node 
    private TreeNode MCTS(TreeNode node, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        TreeNode selectedNode = selection(node);

        TreeNode expandedNode = expansion(selectedNode);

        if (expandedNode != null) {
            performSimulation(selectedNode);
            int bestUtility = -1;
            TreeNode bestChild = null;
            for (int i = 0; i < node.getChildren().size(); i++) {
                if (node.getChildren().get(i).getUtility() > bestUtility) {
                    bestChild = node.getChildren().get(i);
                    bestUtility = (int)(node.getChildren().get(i).getUtility());
                }
            }
            return bestChild;
        }
        System.out.println("Error completing MCTS, returning null");
        return null;
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
        int maxScore = Integer.MIN_VALUE;
        int minScore = Integer.MAX_VALUE;
        for (int i = 0; i < node.getChildren().size(); i++) {
            int score = (int) selectfn(node.getChildren().get(i));
            if (gameType == GameType.SINGLE_PLAYER_GAME 
                || gameType == GameType.TWO_PLAYER_SIMULTANEOUS_GAME) {
                if (score > maxScore) {
                    maxScore = score;
                    selectedNode = node.getChildren().get(i);
                }
            } else if (gameType == GameType.TWO_PLAYER_ALTERNATING_GAME || gameType == GameType.MULTI_PLAYER_GAME) {
                if (node.getOurTurn()) {
                    if (score > maxScore) {
                        maxScore = score;
                        selectedNode = node.getChildren().get(i);
                    }
                } else {
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

    private TreeNode expansion(TreeNode node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if (sharedStateMachine.findTerminalp(node.getState())) {
            return node;
        }
        List<MachineState> nextStates = sharedStateMachine.getNextStates(node.getState());
        for (MachineState state : nextStates) {
            TreeNode nextNode = new TreeNode(state, node);
            List<Move> moves = sharedStateMachine.getRandomJointMove(nextNode.getState());
            nextNode.setOurTurn(!moves.get(ourTurnIndex).toString().equals("noop"));
            node.addChild(nextNode);
        }
        return node.getChildren().get(new Random().nextInt(node.getChildren().size()));
    }

    private int performSimulation(TreeNode node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        //somehow perform random simulation to get to a final state
        TreeNode currNode = node;
        TreeNode nextNode = null;
        while (!sharedStateMachine.findTerminalp(currNode.getState())) {
            List<Move> randomJointMove = sharedStateMachine.getRandomJointMove(currNode.getState());
            nextNode = new TreeNode(sharedStateMachine.getNextState(currNode.getState(), randomJointMove), currNode);
            currNode = nextNode;
        }

        if (currNode != null && sharedStateMachine.findTerminalp(currNode.getState())) {
            int score = sharedStateMachine.findReward(ourRole, currNode.getState());
            backpropagation(currNode, score);
            return score;
        } 

        return 0;
    }


    private void backpropagation(TreeNode node, int score) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        node.incrementVisits();
        node.incrementUtility(score);
        if(node.getParent() != null) {
            backpropagation(node.getParent(), score);
        }
    }
}