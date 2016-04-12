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
 * AlphaBetaGamer` is a very simple state-machine-based Gamer that will always
 * pick randomly from the legal moves it finds at any state in the game.
 */
public final class AlphaBetaGamer extends SampleGamer
{

    int upperThreshold = 100;
    int lowerThreshold = 0;
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
        int alpha = lowerThreshold;
        int beta = upperThreshold;

        System.out.println(moves);
        for(int i = 0; i < moves.size(); i++) {
            int result = minScore(role, moves.get(i), state, alpha, beta);
            System.out.println(moves.get(i) + " score: " + result);
            if(result == upperThreshold) return moves.get(i);
            if(result > score) {
                score = result;
                best = moves.get(i);
            }
        }
        return best;
    }

    private int minScore(Role role, Move move, MachineState state, int alpha, int beta) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {   
        int myIndex = -1;
        List<Role> opponents = getStateMachine().findRoles();
        for(int i = 0; i < opponents.size(); i++) {
            if(role.equals(opponents.get(i))) {
                myIndex = i;
            }
        }

        List<List<Move>> allMoves = new ArrayList<List<Move>>();
        // concatenate all moves into a list of lists
        // each sublist is located at an index corresponding to its role
        for(int i = 0; i < opponents.size(); i++) {
            // get all moves in order of roles 
            if(i == myIndex) {
                List<Move> setMove = new ArrayList<Move>();
                setMove.add(move);
                allMoves.add(setMove);
            } else {
                List<Move> opponentMoves = 
                    getStateMachine().findLegals(opponents.get(i), state);
                allMoves.add(opponentMoves);
            }
        }

        List<List<Move>> allMoveCombos = new ArrayList<List<Move>>(); //FINAL
        for(int i = 0; i < allMoves.size(); i++) {
            List<Move> currMoves = allMoves.get(i);
            if(i == 0) {
                for (int j = 0; j < currMoves.size(); j++) {
                    List<Move> moves = new ArrayList<Move>();
                    moves.add(allMoves.get(i).get(j));
                    allMoveCombos.add(moves);
                }
            } else {
                List<List<Move>> tempMoveCombos = new ArrayList<List<Move>>();
                for (int j = 0; j < allMoveCombos.size(); j++) {
                    for (int k = 0; k < currMoves.size(); k++) {
                        List<Move> newCombo = new ArrayList<Move>(allMoveCombos.get(j));
                        newCombo.add(currMoves.get(k));
                        tempMoveCombos.add(newCombo);
                    }
                }
                allMoveCombos = tempMoveCombos;
            }
        }

        // decide best future state given all move combinations
        for(int i = 0; i < allMoveCombos.size(); i++) {
            MachineState candidateState = 
                getStateMachine().findNext(allMoveCombos.get(i), state);
            //pick highest candidateState
            int result = maxScore(role, candidateState, alpha, beta);
            beta = Math.min(beta, result);
            if(beta <= alpha) {
                return alpha;
            }
        }
        return beta;
    }

    private int maxScore(Role role, MachineState state, int alpha, int beta) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        if(getStateMachine().findTerminalp(state)) {
            return getStateMachine().findReward(role, state);
        }

        List<Move> moves = 
            getStateMachine().findLegals(role, state);

        for(int i = 0; i < moves.size(); i++) {
            int result = minScore(role, moves.get(i), state, alpha, beta);
            alpha = Math.max(alpha, result);
            if(alpha >= beta) {
                return beta;
            }
        }
        return alpha;
    }
}