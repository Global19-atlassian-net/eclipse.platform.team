package org.eclipse.team.internal.ui.jobs;

import java.util.*;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.team.core.subscribers.*;
import org.eclipse.team.core.synchronize.*;

class RefreshChangeListener implements ISubscriberChangeListener {
	private List changes = new ArrayList();
	private SubscriberSyncInfoCollector collector;

	RefreshChangeListener(SubscriberSyncInfoCollector collector) {
		this.collector = collector;
	}
	public void subscriberResourceChanged(ISubscriberChangeEvent[] deltas) {
		for (int i = 0; i < deltas.length; i++) {
			ISubscriberChangeEvent delta = deltas[i];
			if (delta.getFlags() == ISubscriberChangeEvent.SYNC_CHANGED) {
				changes.add(delta);
			}
		}
	}
	public SyncInfo[] getChanges() {
		collector.waitForCollector(new NullProgressMonitor());
		List changedSyncInfos = new ArrayList();
		SyncInfoSet set = collector.getSubscriberSyncInfoSet();
		for (Iterator it = changes.iterator(); it.hasNext();) {
			ISubscriberChangeEvent delta = (ISubscriberChangeEvent) it.next();
			SyncInfo info = set.getSyncInfo(delta.getResource());
			if (info != null && interestingChange(info)) {			
				changedSyncInfos.add(info);
			}
		}
		return (SyncInfo[]) changedSyncInfos.toArray(new SyncInfo[changedSyncInfos.size()]);
	}

	private boolean interestingChange(SyncInfo info) {
		int kind = info.getKind();
		if(isThreeWay()) {
			int direction = SyncInfo.getDirection(kind);
			return (direction == SyncInfo.INCOMING || direction == SyncInfo.CONFLICTING);
		} else {
			return SyncInfo.getChange(kind) != SyncInfo.IN_SYNC;
		}
	}
	
	private boolean isThreeWay() {
		return collector.getSubscriber().getResourceComparator().isThreeWay();
	}
	
	public void clear() {
		changes.clear();
	}
}
