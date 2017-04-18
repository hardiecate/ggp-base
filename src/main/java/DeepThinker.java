import java.util.List;

import org.ggp.base.apps.player.Player;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class DeepThinker extends StateMachineGamer {

	Player p;

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub

	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		StateMachine machine = getStateMachine();
		MachineState state = getCurrentState();
		Role role = getRole();
		List<List<Move>> outcomes = machine.getLegalJointMoves(state);

		Move action = null;
		int score = 0;
		for (int i=0; i<outcomes.size(); i++) {
			  int alpha = 0;
			  int beta = 100;
			  int result = maxscore(role, simulate(state, outcomes.get(i), machine), machine, alpha, beta);
		      if (result==100) {
		    	   return outcomes.get(i).get(0);
		      }
		      if (result>score) {
		    	   	score = result;
		    	   	action = outcomes.get(i).get(0);
		      }
		  }
		  return action;
	}

	public int maxscore(Role role, MachineState state, StateMachine machine, int alpha, int beta) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		if (machine.isTerminal(state)) {
			return machine.getGoal(state, role);
		}

		List<List<Move>> outcomes = machine.getLegalJointMoves(state);
		for (int i = 0; i < outcomes.size(); i++) {
			int result = minscore(role, outcomes.get(i).get(0), simulate(state, outcomes.get(i), machine), machine, alpha, beta);
			alpha = Math.max(alpha, result);
			if (alpha >= beta) {
				return beta;
			}
		}

//		List<Move> actions = machine.getLegalMoves(state, role);
//		for (int i = 0; i < actions.size(); i++) {
//
//			int result = minscore(role, actions.get(i), simulate(state, actions, machine), machine, alpha, beta);
//			alpha = Math.max(alpha, result);
//			if (alpha >= beta) {
//				return beta;
//			}
//		}
		return alpha;
	}

	public int minscore(Role role, Move action, MachineState state, StateMachine machine, int alpha, int beta) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		List<Role> roles = machine.getRoles();
		Role opponent = role;
		for (Role r: roles) {
			if (r != role) {
				opponent = r;
			}
		}


		List<List<Move>> outcomes = machine.getLegalJointMoves(state, opponent, action);
		for (int i = 0; i < outcomes.size(); i++) {
			List<Move> movesPerPlayer = outcomes.get(i);
			MachineState newstate = findNext(movesPerPlayer, state, machine);
			int result = maxscore(role, newstate, machine, alpha, beta);
			beta = Math.min(beta, result);
			if (beta <= alpha) {
				return alpha;
			}
		}
		return beta;
	}

	public MachineState simulate(MachineState state, List<Move> moves, StateMachine machine) throws TransitionDefinitionException{
		if (moves == null) {
			return state;
		} else {
			return machine.getNextState(state, moves);
		}
	}

	public MachineState findNext(List<Move> moves, MachineState state, StateMachine machine) throws TransitionDefinitionException {
		return machine.getNextState(state, moves);
	}

	@Override
	public void stateMachineStop() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub

	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "DeepThinker";
	}

}
