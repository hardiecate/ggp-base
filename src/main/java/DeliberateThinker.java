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

// Makes a random legal move

public class DeliberateThinker extends StateMachineGamer {

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

		return bestmove(role, state, machine);
	}

	public Move bestmove(Role role, MachineState state, StateMachine machine) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		List<Move> moves = machine.getLegalMoves(state, role);
		int score = 0;
		Move move = moves.get(0);
		for (int i = 0; i < moves.size(); i++) {
			System.out.println("Checking the move, " + moves.get(i));
			int result = maxscore(role, simulate(moves.get(i), state, machine), machine);
			if (result == 100) {
				System.out.println("Best move is + " + moves.get(i));
				System.out.println("with the score of 100");
				return moves.get(i);
			}
			if (result > score) {
				score = result;
				move = moves.get(i);
			}
		}
		System.out.println("Best move is " + move);
		System.out.println("with the score of " + score);
		return move;
	}

	public int maxscore(Role role, MachineState state, StateMachine machine) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		if (machine.isTerminal(state)) {
			return machine.getGoal(state, role);
		}
		List<Move> moves = machine.getLegalMoves(state, role);
		int score = 0;
		for (int i = 0; i < moves.size(); i++) {
			int result = maxscore(role, simulate(moves.get(i), state, machine), machine);
			if (result > score) score = result;
		}
		return score;
	}

	public MachineState simulate(Move move, MachineState state, StateMachine machine) throws TransitionDefinitionException {
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
		return "DeliberateThinker";
	}

}