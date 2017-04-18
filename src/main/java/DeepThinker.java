import java.util.ArrayList;
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
		List<Move> moves = machine.getLegalMoves(state, role);

		Move action =  moves.get(0);
		int score = 0;
		for (int i=0; i<moves.size(); i++) {
			  int alpha = machine.getGoal(state, role);
			  int beta = machine.getGoal(state, role);
			  int result = maxscore(role, simulate(state, moves.get(i), machine), machine, alpha, beta);
		      if (result==100) {
		    	   return moves.get(i);
		      }
		      if (result>score) {
		    	   	score = result;
		    	   	action = moves.get(i);
		      }
		  }
		  return action;
	}

	public int maxscore(Role role, MachineState state, StateMachine machine, int alpha, int beta) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		if (machine.isTerminal(state)) {
			return machine.getGoal(state, role);
		}
		List<Move> actions = machine.getLegalMoves(state, role);
		for (int i = 0; i < actions.size(); i++) {
			int result = minscore(role, actions.get(i), simulate(state, actions.get(i), machine), machine, alpha, beta);
			alpha = Math.max(alpha, result);
			if (alpha >= beta) {
				return beta;
			}
		}
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
		List<Move> actions = machine.getLegalMoves(state, opponent);
		for (int i = 0; i < actions.size(); i++) {
			Move move;
			if (role == roles.get(0)) {
				move = action;
			} else {
				move = actions.get(i);
			}
			MachineState newstate = findNext(move, state, machine);
			int result = maxscore(role, state, machine, alpha, beta);
			beta = Math.min(beta, result);
			if (beta <= alpha) {
				return alpha;
			}
		}
		return beta;
	}

	public MachineState simulate(MachineState state, Move move, StateMachine machine) throws TransitionDefinitionException{
		if (move == null) {
			return state;
		} else {
			return findNext(move, state, machine);
		}
	}

	public MachineState findNext(Move move, MachineState state, StateMachine machine) throws TransitionDefinitionException {
		List<Move> moves = new ArrayList<Move>();
		moves.add(move);
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
