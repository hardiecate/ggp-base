package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.lang.Math;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.StateMachine;


class TreeNode
{
    MachineState nodeState;
    int numEncounters;
    int totalReward;
    List<Move> legalMoves;
    Map<Move, List<List<Move>>> legalMoveMap;
    TreeNode parent;
    boolean isTerminal;
    List<TreeNode> children;

    public TreeNode(MachineState state, StateMachine stateMachine, Role ourRole, TreeNode parentNode) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        nodeState = state;
        numEncounters = 0;
        totalReward = 0;
        isTerminal = stateMachine.findTerminalp(state);
        parent = parentNode;
        children = new ArrayList<TreeNode>();
        legalMoveMap = new HashMap<Move, List<List<Move>>>();
        legalMoves = null;
        if (!isTerminal) {
            legalMoves = stateMachine.findLegals(ourRole, state);
        }
    }

    public int getExpectedReward()
    {
        if (numEncounters == 0) return 0;
        return (int)((double) totalReward / (double) numEncounters);
    }

    @Override
    public boolean equals(Object otherNode)
    {
        return nodeState.equals(((TreeNode) otherNode).nodeState);
    }

    @Override
    public int hashCode()
    {
        return nodeState.hashCode();
    }
}


public final class MCTS2Gamer extends SampleGamer
{
    // Set timeout stats
    private long minMetaTimeLeft = PREFERRED_METAGAME_BUFFER; 
    private long minPlayTimeLeft = PREFERRED_PLAY_BUFFER;
    // General game info
    private Role ourRole = null;
    private int kMinReward = 0;
    private int kMaxReward = 100;
    private StateMachine stateMachine = null;
    // General game state trackers
    private int numTurnsTaken = 0;
    private int currentDepth = 0;
    private Move lastMove = null;
    // Monte Carlo Tree Search variabless
    Map<Integer, TreeNode> nodesEncountered = null; //from state hashcode to node
    private int numProbes = 50;
    private int explorationDepth = 1;
    private TreeNode lastNode = null; 
    private long currStartTime = 0;
    private long currTimeout = 0;

    //bullshit 2 player special case stuff
    private boolean twoPlayerTurnGame = false;
    private Role otherRole = null;

    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        long start = System.currentTimeMillis();
        currStartTime = start;
        currTimeout = timeout;

        // initialize private variables
        stateMachine = getStateMachine();
        ourRole = getRole();
        nodesEncountered = new HashMap<Integer, TreeNode>();

        // set MCTS root node
        MachineState initialState = stateMachine.findInits();
        TreeNode rootNode = new TreeNode(initialState, stateMachine, ourRole, null);
        nodesEncountered.put(rootNode.hashCode(), rootNode);
        lastNode = rootNode;

        twoPlayerTurnGame = (stateMachine.getRoles().size() == 2);
        for (Role role : stateMachine.getRoles()) {
            if (!role.equals(ourRole)) {
                otherRole = role;
            }
        }
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        long start = System.currentTimeMillis();
        currStartTime = start;
        currTimeout = timeout;

        MachineState currState = getCurrentState();
        Integer currStateHash = currState.hashCode();
        TreeNode currNode = nodesEncountered.get(currStateHash);

        // shouldn't be called
        if (currNode == null) {
            currNode = new TreeNode(currState, stateMachine, ourRole, null);
            nodesEncountered.put(currStateHash, currNode);
        }

        lastNode = currNode;
        List<Move> moves = currNode.legalMoves;

        Move selection = bestMove(currNode); 

        // finish turn and update
        numTurnsTaken++;
        lastMove = selection;

        System.out.println("Nodes explored so far: " + nodesEncountered.size());

        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
    }

    private int minScore(Move move, TreeNode currNode, int depth, int alpha, int beta) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {   
        List<List<Move>> possibleJointMoves = currNode.legalMoveMap.get(move);
        if (possibleJointMoves == null) {
            possibleJointMoves = stateMachine.getLegalJointMoves(currNode.nodeState, ourRole, move);
            currNode.legalMoveMap.put(move, possibleJointMoves);
        }

        for (int i = 0; i < possibleJointMoves.size(); i++) {
            List<Move> jointMove = possibleJointMoves.get(i);
            MachineState candidateState = stateMachine.findNext(jointMove, currNode.nodeState);

            // create new node if one does not exist
            TreeNode nextNode = nodesEncountered.get(candidateState.hashCode());
            if (nextNode == null) {
                nextNode = new TreeNode(candidateState, stateMachine, ourRole, currNode);
                nodesEncountered.put(candidateState.hashCode(), nextNode);
            }
            if (!currNode.children.contains(nextNode)) {
                currNode.children.add(nextNode);
            }
            int result = maxScore(nextNode, depth, alpha, beta);
            beta = Math.min(beta, result);
            if (beta <= alpha) {
                return alpha;
            }
        }
        return beta;
    }

    private int maxScore(TreeNode currNode, int depth, int alpha, int beta) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {   
        if (currNode.isTerminal) {
            int reward = stateMachine.findReward(ourRole, currNode.nodeState);
            if (twoPlayerTurnGame) {
                return Math.max(0, reward - stateMachine.findReward(otherRole, currNode.nodeState));
            }
            return reward;
        }
        if (currTimeout - System.currentTimeMillis() < minPlayTimeLeft) {
            System.out.println("We've timed out maxScore. Time elapsed: " + (System.currentTimeMillis() - currStartTime) + " | timeout buffer: " + minPlayTimeLeft + " | time left: " + (currTimeout - System.currentTimeMillis()));
            return currNode.getExpectedReward();
        }
        if (depth >= explorationDepth) {
            return monteCarlo(currNode);
        }
        List<Move> moves = currNode.legalMoves;
        int score = 0;
        for (int i = 0; i < moves.size(); i++) {
            int result = minScore(moves.get(i), currNode, depth + 1, alpha, beta);
            alpha = Math.max(alpha, result);
            if(alpha >= beta) {
               return beta;
            }
        }
        return alpha;
    }

    private Move bestMove(TreeNode currNode) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        List<Move> moves = currNode.legalMoves;
        MachineState currState = currNode.nodeState;
        int bestScore = kMinReward;
        Move bestMove = moves.get(0);
        int alpha = -kMaxReward;
        int beta = kMaxReward + 1;
        for (int i = 0; i < moves.size(); i++) {
            // check for timeout
            if (currTimeout - System.currentTimeMillis() < minPlayTimeLeft) {
                System.out.println(currTimeout - System.currentTimeMillis());
                return bestMove;
            }
            int result = minScore(moves.get(i), currNode, 0, alpha, beta);
            if (result > bestScore) {
                bestScore = result;
                bestMove = moves.get(i);
            }
        }
        return bestMove;
    }

    private int monteCarlo(TreeNode currNode) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        TreeNode selectedNode = selection(currNode);
        double totalScore = 0;
        int numChargesCompleted = 0;
        int score = 0;
        for (; numChargesCompleted < numProbes; numChargesCompleted++) {
            if (currTimeout - System.currentTimeMillis() < minPlayTimeLeft) {
                break;
            }
            int currScore = depthCharge(currNode);
            if (currScore == -1) {
                break;
            } else {
                backPropagate(selectedNode, currScore);
            }
            totalScore += currScore;
        }
        if (numChargesCompleted != 0) {
            score = (int)((double)totalScore / (double)numChargesCompleted);
        }
        System.out.println("Completed " + numChargesCompleted + " of " + numProbes + " probes for a total of:  " + score);
        return score;
    }

    private int depthCharge(TreeNode currNode) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if (currNode.isTerminal) {
            int reward = stateMachine.findReward(ourRole, currNode.nodeState);
            if (twoPlayerTurnGame) {
                return Math.max(0, reward - stateMachine.findReward(otherRole, currNode.nodeState));
            }
            return reward;
        }
        if (currTimeout - System.currentTimeMillis() < minPlayTimeLeft) return -1;
        List<Move> randomJointMoves = stateMachine.getRandomJointMove(currNode.nodeState);
        MachineState nextState = stateMachine.getNextState(currNode.nodeState, randomJointMoves);
        TreeNode nextNode = nodesEncountered.get(nextState.hashCode());
        if (nextNode == null) {
            nextNode = new TreeNode(nextState, stateMachine, ourRole, currNode);
            nodesEncountered.put(nextState.hashCode(), nextNode);
        }
        if (!currNode.children.contains(nextNode)) {
            currNode.children.add(nextNode);
        }
        if (currTimeout - System.currentTimeMillis() < minPlayTimeLeft) return -1;
        return depthCharge(nextNode);
    }

    private void backPropagate(TreeNode currNode, int score) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        currNode.numEncounters++;
        currNode.totalReward += score;
        if (currNode.parent != null) {
            backPropagate(currNode.parent, score);
        }
    }

    private double selectfn(TreeNode currNode) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        return currNode.totalReward + Math.sqrt(2 * Math.log(currNode.parent.numEncounters / currNode.numEncounters));
    }

    private TreeNode selection(TreeNode currNode) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if (currNode.numEncounters == 0) {
            return currNode;
        }
        for (TreeNode child : currNode.children) {
            if (child.numEncounters == 0) {
                return child;
            }
        }
        int score = 0;
        TreeNode result = null;
        for (TreeNode child : currNode.children) {
            int newScore = (int)Math.round(selectfn(child));
            if (newScore >= score) {
                score = newScore;
                result = child;
            }
        }
        return result;
    }

}




