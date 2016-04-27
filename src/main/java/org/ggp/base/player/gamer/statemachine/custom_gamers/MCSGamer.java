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
public final class MCSGamer extends SampleGamer
{
    int maxLevels = 10;
    int nProbes = 4;

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

    private int minScore(Role role, Move move, MachineState state, int level) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {  
        List<List<Move>> allMoveCombos = getStateMachine().getLegalJointMoves(state, role, move);

        int score = 0;
        for(int i = 0; i < allMoveCombos.size(); i++) {
            MachineState candidateState = getStateMachine().findNext(allMoveCombos.get(i), state);
            //pick highest candidateState
            int result = maxScore(role, candidateState, level);
            if (result >= score) {
                score = result;
            }
        }
        return score;
    }

    private Move bestMove(Role role, MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException 
    {
        List<Move> moves = getStateMachine().findLegals(role, state);
        Move best = moves.get(0);
        int score = 0;

        for(int i = 0; i < moves.size(); i++) {
            int result = minScore(role, moves.get(i), state, 0);
            if(result > score) {
                score = result;
                best = moves.get(i);
            }
        }
        return best;
    }

    private int maxScore(Role role, MachineState state, int level) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if(getStateMachine().findTerminalp(state)) {
            return getStateMachine().findReward(role, state);
        }
        if (level >= maxLevels) {
            return (int)monteCarlo(role, state);
        }

        List<Move> moves = 
            getStateMachine().findLegals(role, state);

        int score = 0;
        for(int i = 0; i < moves.size(); i++) {
            int result = minScore(role, moves.get(i), state, level + 1);
            if (result == 100) {
                return 100;
            }
            if (result >= score) {
                score = result;
            } 
        }
        return score;
    }

    private double monteCarlo(Role role, MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        double totalScore = 0;
        for (int i = 0; i < nProbes; i++) {
            totalScore += depthCharge(role, state);
        }
        return totalScore / nProbes;
    }

    private int depthCharge(Role role, MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if(getStateMachine().findTerminalp(state)) {
            return getStateMachine().findReward(role, state);
        }

        List<Role> roles = getStateMachine().getRoles();
        ArrayList<Move> randomMoves = new ArrayList<Move>(roles.size());
        for (int i = 0; i < roles.size(); i++) {
            List<Move> moves = getStateMachine().findLegals(role, state);
            int randIndex = new Random().nextInt(moves.size());
            Move randomMove = moves.get(randIndex);
            randomMoves.set(i, randomMove);
        }

        MachineState newState = getStateMachine().getNextState(state, randomMoves);
        return depthCharge(role, newState);
    }
}