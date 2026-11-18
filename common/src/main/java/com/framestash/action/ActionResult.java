package com.framestash.action;

import com.framestash.label.LabelledContainer;
import java.util.List;

/** What an action actually did: items moved and the containers that took part. */
public record ActionResult(long items, List<LabelledContainer> containers) {
    public static final ActionResult NOTHING = new ActionResult(0, List.of());
}
