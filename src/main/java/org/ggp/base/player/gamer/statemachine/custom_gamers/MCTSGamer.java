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

/**
 * MCSGamer
 */
public final class MCTSGamer extends SampleGamer
{

    // Hyperparameters - change based on gameplay
    int maxLevels = 1;
    int nProbes = 2;

    // Constants
    int upperThreshold = 100;
    int lowerThreshold = 0;

    // Timer
    int timeRemaining = 0;

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
    }

    /*
     * This function is called at the start of each round
     * You are required to return the Move your player will play
     * before the timeout.
     *
     */
    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        long start = System.currentTimeMillis();

        List<Move> moves = getStateMachine().findLegals(getRole(), getCurrentState());

        Move selection = moves.get(0);
        
        //only do search if there is more than one move to choose from
        if (moves.size() > 1)
            selection = bestMove(getRole(), getCurrentState());

        long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
    }

    private int minScore(Role role, Move move, MachineState state, int level, int alpha, int beta) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {  
        List<List<Move>> allMoveCombos = getStateMachine().getLegalJointMoves(state, role, move);

        int score = 100;
        for(int i = 0; i < allMoveCombos.size(); i++) {
            MachineState candidateState = getStateMachine().findNext(allMoveCombos.get(i), state);
            //pick highest candidateState
            int result = maxScore(role, candidateState, level, alpha, beta);
            beta = Math.min(beta, result);
            if(beta <= alpha) {
                return alpha;
            }
        }
        return beta;
    }

    private Move bestMove(Role role, MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException 
    {
        List<Move> moves = getStateMachine().findLegals(role, state);
        Move best = moves.get(0);
        int score = 0;
        int alpha = -upperThreshold;
        int beta = upperThreshold + 1;

        for(int i = 0; i < moves.size(); i++) {
            int result = minScore(role, moves.get(i), state, 0, alpha, beta);
            if(result > score) {
                score = result;
                best = moves.get(i);
            }
        }
        return best;
    }

    private int maxScore(Role role, MachineState state, int level, int alpha, int beta) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if(getStateMachine().findTerminalp(state)) {
            return getStateMachine().findReward(role, state);
        }
        if (level >= maxLevels) {
            return (int)monteCarloTree(role, state);
        }

        List<Move> moves = 
            getStateMachine().findLegals(role, state);

        int score = 0;
        for(int i = 0; i < moves.size(); i++) {
            int result = minScore(role, moves.get(i), state, level + 1, alpha, beta);
            if (result == 100) {
                return 100;
            }
            alpha = Math.max(alpha, result);
            if(alpha >= beta) {
                return beta;
            }
        }
        return alpha;
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

    private double monteCarloTree(Role role, MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        TreeNode initialNode = new TreeNode(state, null); // TODO(Adi): fix with proper parent when tree is constructed during minimax

        if(timeRemaining > 0) {
            TreeNode selectedNode = selection(initialNode);
            int score = expansion(selectedNode, role);  
            backpropagation(selectedNode, score);
        }

        return 0.0; // TODO(Adi): fix by selecting optimal action based on computed utilities
    }

    private TreeNode selection(TreeNode node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if(node.getVisits() == 0) {
            return node;
        }
        for (int i = 0; i < node.getChildren().size(); i++) {
            if(node.getChildren().get(i).getVisits() == 0) {
                return node.getChildren().get(i);
            }
        }

        int score = 0;
        TreeNode result = node;
        for (int i = 0; i < node.getChildren().size(); i++) {
            int newScore = (int)Math.round(selectfn(node.getChildren().get(i)));
            if(newScore > score) {
                score = newScore;
                result = node.getChildren().get(i);
            }
        }

        return selection(result);
    }

    private double selectfn(TreeNode node) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        return node.getUtility() + Math.sqrt(2 * Math.log(node.getParent().getVisits()/node.getVisits()));
    }

    private int expansion(TreeNode node, Role role) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if(getStateMachine().findTerminalp(node.getState())) {
            return getStateMachine().findReward(role, node.getState());
        }

        List<Role> roles = getStateMachine().getRoles();
        for (int i = 0; i < roles.size(); i++) {
            List<Move> moves = getStateMachine().findLegals(roles.get(i), node.getState());

            for(int j = 0; j < moves.size(); j++) {
                ArrayList<Move> actionSingleton = new ArrayList<Move>(1);
                actionSingleton.add(moves.get(j));
                MachineState newState = getStateMachine().getNextState(node.getState(), actionSingleton);
                TreeNode newNode = new TreeNode(newState, node);
                node.addChild(newNode);
                return expansion(newNode, role);
            }
        }

        return 0; // should never happen?
    }

    private void backpropagation(TreeNode node, int score) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        node.incrementVisits();
        node.setUtility(node.getUtility() + (double)score);
        if(node.getParent() != null) {
            backpropagation(node.getParent(), score);
        }
    }
}