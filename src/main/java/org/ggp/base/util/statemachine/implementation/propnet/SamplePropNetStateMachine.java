package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.Collection;

import java.lang.*;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.gdl.grammar.GdlLiteral;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.components.*;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;
import org.ggp.base.util.gdl.factory.*;

@SuppressWarnings("unused")
public class SamplePropNetStateMachine extends StateMachine {
    
    // print wrapper
    private static final boolean SHOWPRINTS = true;
    private void PRINT(String str) { if (SHOWPRINTS) System.out.println(str); }

    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering = null;
    /** The player roles */
    private List<Role> roles;

    // Initial state calculated on init
    private MachineState initialState = null;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     *
     * Andrew - I did not do any ordering calculations because we backwards propagate
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
            propNet = OptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            initialState = setInitialMachineState();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     *
     * Andrew - Not sure if this works (> 50%)
     */
    @Override
    public boolean isTerminal(MachineState state) {
        markBasePropsForState(state);
        return getComponentVal(propNet.getTerminalProposition());
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     *
     * Andrew - Not sure if this works
     */
    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
        markBasePropsForState(state);
        Set<Proposition> goalPropositions = propNet.getGoalPropositions().get(role);
        if (goalPropositions == null || goalPropositions.size() != 1) {
            throw new GoalDefinitionException(state, role);
        } else {
            Proposition goalProp = (Proposition)(goalPropositions.iterator().next());
            return getGoalValue(goalProp);
        }
    }

    /**
     * Sets truth value of init proposition to true and then computes
     * the resulting State. Called only once during initialization.
     *
     * Andrew - works
     */
    private MachineState setInitialMachineState() {
        clearBaseProps();
        Proposition initProp = propNet.getInitProposition();
        initProp.setValue(true);
        return getStateFromBase();
    }


    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     *
     * Andrew - works 
     * remember to set initialState in setInitialMachineState()
     */
    @Override
    public MachineState getInitialState() {
        return initialState;
    }

    /**
     * Computes all possible actions for role.
     * Currently returns actions for all roles
     *
     * Andrew - works (99%)
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
        Collection<Proposition> inputProps = propNet.getInputPropositions().values();
        List<Move> actions = new ArrayList<Move>();
        for (Proposition p : inputProps) {
            GdlRelation relation = (GdlRelation) p.getName();
            if ((role.toString()).equals(relation.getBody().get(0).toString())) {
                actions.add(getMoveFromProposition(p));
            }
        }
        return actions;
    }

    /**
     * Computes the legal moves for role in state.
     *
     * Andrew - unsure if it works (< 50 %)
     *
     * I think this should be right but we might also
     * need to verify that each legal proposition maps to a valid 
     * member of findActions(role);
     */
    @Override
    public List<Move> getLegalMoves(MachineState state, Role role)
            throws MoveDefinitionException {
        markBasePropsForState(state);
        List<Move> legalMoves = new ArrayList<Move>();
        GdlTerm roleTerm = (GdlTerm)(role.getName());
        Set<Proposition> legalProps = propNet.getLegalPropositions().get(role);
        for (Proposition prop : legalProps) {
            if (prop.getName().get(0).equals(roleTerm)) {
                legalMoves.add(new Move(prop.getName().get(1)));
            }
        }
        return legalMoves;
    }

    /**
     * Computes the next state given state and the list of moves.
     * 
     * Andrew - uncompleted 
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
        markInputPropsForJointMove(moves);
        markBasePropsForState(state);
        ArrayList<Proposition> currBaseProps = new ArrayList<Proposition>(propNet.getBasePropositions().values());
        HashSet<GdlSentence> nextStateContents = new HashSet<GdlSentence>();
        for (Proposition prop : currBaseProps) {
            boolean val = getComponentVal(prop);
            prop.setValue(val);
            if (val) nextStateContents.add(prop.getName());
        }
        return new MachineState(nextStateContents);
    }

    /**
     * NOTE: currently a NOOP because we do backwards prop
     *
     * This should compute the topological ordering of propositions.
     * Each component is either a proposition, logical gate, or transition.
     * Logical gates and transitions only have propositions as inputs.
     *
     * The base propositions and input propositions should always be exempt
     * from this ordering.
     *
     * The base propositions values are set from the MachineState that
     * operations are performed on and the input propositions are set from
     * the Moves that operations are performed on as well (if any).
     *
     * @return The order in which the truth values of propositions need to be set.
     */
    public List<Proposition> getOrdering() { return new ArrayList<Proposition>(); }

    /* Propnet methods */
    // equivalent to ch 10 notes markbases
    private void markBasePropsForState(MachineState state)
    {
        clearBaseProps();
        Set<GdlSentence> contents = state.getContents();
        Map<GdlSentence, Proposition> propMap = propNet.getBasePropositions();
        for (GdlSentence s : contents) {
            Proposition currBaseProp = propMap.get(s);
            if (currBaseProp != null) {
                boolean oldVal = currBaseProp.getValue();
                Boolean newVal = Boolean.valueOf(s.getName().getValue());
                currBaseProp.setValue(newVal);
            } else {
                PRINT("Unable to find corresponding base prop for sentence");
            }
        }
    }

    // equivalent to ch 10 notes markactions
    private void markInputPropsForJointMove(List<Move> moves)
    {
        Map<GdlSentence, Proposition> propMap = propNet.getInputPropositions();
        List<GdlSentence> doeses = toDoes(moves);
        ArrayList<Proposition> inputProps = new ArrayList<Proposition>();
        for (GdlSentence does : doeses) {
            inputProps.add(new Proposition(does));
        }
        for (Proposition prop : inputProps) {
            Proposition realInputProp = propMap.get(prop.getName());
            if (realInputProp != null) {
                boolean oldVal = realInputProp.getValue();
                boolean newVal = prop.getValue();
                realInputProp.setValue(newVal);
            } else {
                PRINT("Unable to find corresponding input prop for sentence");
            }
        }
    }

    // equivalent to ch 10 notes clearpropnet(propnet)
    private void clearBaseProps()
    {
        List<Proposition> baseProps = new ArrayList<Proposition>(propNet.getBasePropositions().values());
        for (Proposition prop : baseProps) {
            prop.setValue(false);
        }
    }

    // equivalent to ch 10 notes propmarkp(p)
    private boolean getComponentVal(Component comp)
    {
        if (comp.getInputs().size() == 0) {
            PRINT("Our component has no input nodes!!!");
        }
        if (comp instanceof Proposition) {
            Proposition prop = (Proposition)comp;
            if (propNet.getBasePropositions().values().contains(prop) || propNet.getInputPropositions().values().contains(prop)) {
                // base or input proposition
                return prop.getValue();
            } else {
                PRINT("Current proposition not base or input w/ " + prop.getInputs().size() + " inputs");
                if (prop.getInputs().size() == 1) {
                    return getComponentVal(prop.getSingleInput());
                } else {
                    PRINT("Incorrectly assumed that proposition had more than 1 input");
                    return false;
                }
            }
        } 
        else if (comp instanceof And) {
            return getAndVal(comp);
        } 
        else if (comp instanceof Or) {
            return getOrVal(comp);
        } 
        else if (comp instanceof Not) {
            return getNotVal(comp);
        } 
        // Haven't handled the case of transitions or constants
        else if (comp instanceof Transition) {
            PRINT("Found transition - Recursing on input proposition");
            return getComponentVal(comp.getSingleInput());
        } 
        else if (comp instanceof Constant) {
            Constant constant = (Constant)comp;
            PRINT("Woah haven't seen a constant before: " + constant.toString());
            PRINT("Constant has " + comp.getInputs().size() + " inputs");
            return constant.getValue();
        }
        return false;
    }

    // ch 10 propmarknegation (p)
    private boolean getNotVal(Component comp)
    {
        return !getComponentVal(comp);
    }

    // ch 10 propmarkconjunction (p)
    private boolean getAndVal(Component comp)
    {
        List<Component> inputs = new ArrayList<Component>(comp.getInputs());
        for (Component input : inputs) {
            if (!getComponentVal(input)) {
                return false;
            }
        }
        return true;
    }

    // ch 10 propmarkdisjunction (p)
    private boolean getOrVal(Component comp)
    {
        List<Component> inputs = new ArrayList<Component>(comp.getInputs());
        for (Component input : inputs) {
            if (getComponentVal(input)) {
                return true;
            }
        }
        return false;
    }


    /* Already implemented for you */
    @Override
    public List<Role> getRoles() {
        return roles;
    }

    /* Helper methods */

    /**
     * The Input propositions are indexed by (does ?player ?action).
     *
     * This translates a list of Moves (backed by a sentence that is simply ?action)
     * into GdlSentences that can be used to get Propositions from inputPropositions.
     * and accordingly set their values etc.  This is a naive implementation when coupled with
     * setting input values, feel free to change this for a more efficient implementation.
     *
     * @param moves
     * @return
     */
    private List<GdlSentence> toDoes(List<Move> moves)
    {
        List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
        Map<Role, Integer> roleIndices = getRoleIndices();

        for (int i = 0; i < roles.size(); i++)
        {
            int index = roleIndices.get(roles.get(i));
            doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
        }
        return doeses;
    }

    /**
     * Takes in a Legal Proposition and returns the appropriate corresponding Move
     * @param p
     * @return a PropNetMove
     */
    public static Move getMoveFromProposition(Proposition p) 
    {
        return new Move(p.getName().get(1));
    }

    /**
     * Helper method for parsing the value of a goal proposition
     * @param goalProposition
     * @return the integer value of the goal proposition
     */
    private int getGoalValue(Proposition goalProposition)
    {
        GdlRelation relation = (GdlRelation) goalProposition.getName();
        GdlConstant constant = (GdlConstant) relation.get(1);
        return Integer.parseInt(constant.toString());
    }

    /**
     * A Naive implementation that computes a PropNetMachineState
     * from the true BasePropositions.  This is correct but slower than more advanced implementations
     * You need not use this method!
     * @return PropNetMachineState
     */
    public MachineState getStateFromBase()
    {
        Set<GdlSentence> contents = new HashSet<GdlSentence>();
        for (Proposition p : propNet.getBasePropositions().values())
        {
            p.setValue(p.getSingleInput().getValue());
            if (p.getValue())
            {
                contents.add(p.getName());
            }

        }
        return new MachineState(contents);
    }
}