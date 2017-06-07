package org.ggp.base.util.statemachine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.GdlSentence;

public class MachineState {
    private int visits;
    private int utility;
    private List<MachineState> children;
    private List<MachineState> parents;

	public MachineState() {
        this.contents = null;
        this.setVisits(0);
        this.setUtility(0);
        this.setChildren(new ArrayList<MachineState>());
        this.setParents(new ArrayList<MachineState>());

    }

    /**
     * Starts with a simple implementation of a MachineState. StateMachines that
     * want to do more advanced things can subclass this implementation, but for
     * many cases this will do exactly what we want.
     */
    private final Set<GdlSentence> contents;
    public MachineState(Set<GdlSentence> contents)
    {
        this.contents = contents;
    }

    /**
     * getContents returns the GDL sentences which determine the current state
     * of the game being played. Two given states with identical GDL sentences
     * should be identical states of the game.
     */
    public Set<GdlSentence> getContents()
    {
        return contents;
    }

    @Override
    public MachineState clone() {
        return new MachineState(new HashSet<GdlSentence>(contents));
    }

    /* Utility methods */
    @Override
    public int hashCode()
    {
        return getContents().hashCode();
    }

    @Override
    public String toString()
    {
        Set<GdlSentence> contents = getContents();
        if(contents == null)
            return "(MachineState with null contents)";
        else
            return contents.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if ((o != null) && (o instanceof MachineState))
        {
            MachineState state = (MachineState) o;
            return state.getContents().equals(getContents());
        }

        return false;
    }

	public int getVisits() {
		return visits;
	}

	public void setVisits(int visits) {
		this.visits = visits;
	}

	public int getUtility() {
		return utility;
	}

	public void setUtility(int utility) {
		this.utility = utility;
	}

	public List<MachineState> getChildren() {
		return children;
	}

	public void setChildren(List<MachineState> children) {
		this.children = children;
	}

	public void addChild(MachineState child) {
		this.children.add(child);
	}

	public List<MachineState> getParents() {
		return parents;
	}

	public void setParents(List<MachineState> parents) {
		this.parents = parents;
	}

	public void addParent(MachineState parent) {
		this.parents.add(parent);
	}
}