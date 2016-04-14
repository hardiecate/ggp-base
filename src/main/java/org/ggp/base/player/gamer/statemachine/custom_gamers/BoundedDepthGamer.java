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
 * AlphaBetaGamer implements minimax with alpha-beta pruning
 */
public final class BoundedDepthGamer extends SampleGamer
{
    int maxDepth = 5;

    /**
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

    // Returns the best move for the supplied role given a set of possible moves
    private Move bestMove(Role role, MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException 
    {
        List<Move> moves = getStateMachine().findLegals(role, state);
        Move best = moves.get(0);
        int score = 0;

        for(int i = 0; i < moves.size(); i++) {
            int result = minScore(role, moves.get(i), state, 0);
            if(result == 100) return moves.get(i);
            if(result > score) {
                score = result;
                best = moves.get(i);
            }
        }
        return best;
    }

    private int evalFn(Role role, MachineState state) throws MoveDefinitionException
    {
        return mobility(role, state);
    }

    private int mobility(Role role, MachineState state) throws MoveDefinitionException
    {
        double numLegalMoves = getStateMachine().findLegals(role, state).size();
        double numTotalMoves = getStateMachine().findActions(role).size();
        return (int)((numLegalMoves/numTotalMoves) * 100);
    }

    private int focus(Role role, MachineState state) throws MoveDefinitionException
    {
        return 100 - mobility(role, state);
    }

    private int goalProximity(Role role, MachineState state) throws GoalDefinitionException
    {
        return getStateMachine().findReward(role, state);
    }

    //adjust these
    double kMobilityWeight = 0.4;
    double kFocusWeight = 0.1;
    double kGoalProximityWeight = 0.5;

    private int weightedComboFn(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException
    {
        int result = (int)(kMobilityWeight * mobility(role, state)) + (int)(kFocusWeight * focus(role, state)) + (int)(kGoalProximityWeight * goalProximity(role, state));
        return Math.max(result, 100);
    }

    private int minScore(Role role, Move move, MachineState state, int level) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {   
        int myIndex = -1;
        List<Role> opponents = getStateMachine().findRoles();
        for(int i = 0; i < opponents.size(); i++) {
            if(role.equals(opponents.get(i))) {
                myIndex = i;
            }
        }

        List<List<Move>> allMoveCombos = getStateMachine().getLegalJointMoves(state, role, move);
        //System.out.println(allMoveCombos);
        // decide best future state given all move combinations
        int score = 100;
        for(int i = 0; i < allMoveCombos.size(); i++) {
            MachineState candidateState = getStateMachine().findNext(allMoveCombos.get(i), state);
            int result = maxScore(role, candidateState, level + 1);
            if (result == 0) {
                return 0;
            }
            if (result < score) {
                score = result;
            }
        }
        return score;
    }

    private int maxScore(Role role, MachineState state, int level) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if(getStateMachine().findTerminalp(state)) {
            return getStateMachine().findReward(role, state);
        }
        if (level >= maxDepth) {
            return evalFn(role, state);
        } 

        List<Move> moves = 
            getStateMachine().findLegals(role, state);

        int score = 0;
        for(int i = 0; i < moves.size(); i++) {
            int result = minScore(role, moves.get(i), state, level);
            if (result == 100) {
                return 100;
            }
            if (result > score) {
                score = result;
            }
        }
        return score;
    }
}