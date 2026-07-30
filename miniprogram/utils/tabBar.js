function tabIndicatorTransform(index) {
  return `translate3d(${index * 100}%, 0, 0)`;
}

function syncTabBar(page, nextIndex) {
  const app = getApp();
  const previousIndex = app.globalData.activeTabIndex;
  const pending = app.globalData.tabBarTransition;
  const hasPendingTransition = pending
    && pending.to === nextIndex
    && Date.now() - pending.startedAt < 1000;
  const fromIndex = hasPendingTransition ? pending.from : previousIndex;

  app.globalData.activeTabIndex = nextIndex;
  app.globalData.tabBarTransition = null;

  if (!page || typeof page.getTabBar !== 'function') return;
  const tabBar = page.getTabBar();
  if (!tabBar || typeof tabBar.moveTo !== 'function') return;

  if (Number.isInteger(fromIndex) && fromIndex !== nextIndex && typeof tabBar.moveFromTo === 'function') {
    tabBar.moveFromTo(fromIndex, nextIndex);
    return;
  }
  tabBar.moveTo(nextIndex, false);
}

module.exports = { syncTabBar, tabIndicatorTransform };
