package tc.oc.pgm.modes;

import static net.kyori.adventure.key.Key.key;
import static net.kyori.adventure.sound.Sound.sound;
import static net.kyori.adventure.text.Component.text;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.module.exception.ModuleLoadException;
import tc.oc.pgm.countdowns.CountdownContext;
import tc.oc.pgm.countdowns.MatchCountdown;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.filters.FilterMatchModule;

@ListenerScope(MatchScope.LOADED)
public class ObjectiveModesMatchModule implements MatchModule, Listener {

  private static final Sound SOUND =
      sound(key("mob.zombie.remedy"), Sound.Source.MASTER, 0.15f, 1.2f);

  private final Match match;
  private final List<Mode> modes;
  private final List<ModeChangeCountdown> countdowns;
  private final CountdownContext countdownContext;

  public ObjectiveModesMatchModule(Match match, List<Mode> modes) {
    this.match = match;
    this.modes = modes;
    this.countdowns = new ArrayList<>(this.modes.size());
    this.countdownContext = new CountdownContext(match, match.getLogger());
  }

  @Override
  public void load() throws ModuleLoadException {
    FilterMatchModule fmm = match.needModule(FilterMatchModule.class);
    for (Mode mode : this.modes) {
      ModeChangeCountdown countdown = new ModeChangeCountdown(match, this, mode);
      this.countdowns.add(countdown);
      if (mode.getFilter() != null) {
        // if filter returns ALLOW at any time in the match, start countdown for mode change
        fmm.onRise(
            Match.class,
            mode.getFilter(),
            listener -> {
              if (!this.countdownContext.isRunning(countdown) && match.isRunning()) {
                this.countdownContext.start(countdown, countdown.getMode().getAfter());
              }
            });
      }
    }
  }

  @Override
  public void enable() {
    for (ModeChangeCountdown countdown : this.countdowns) {
      if (countdown.getMode().getFilter() == null) {
        this.countdownContext.start(countdown, countdown.getMode().getAfter());
      }
    }
  }

  @Override
  public void disable() {
    for (ModeChangeCountdown countdown : this.getAllCountdowns()) {
      this.countdownContext.cancel(countdown);
    }
  }

  public CountdownContext getCountdown() {
    return this.countdownContext;
  }

  public List<ModeChangeCountdown> getAllCountdowns() {
    return new ImmutableList.Builder<ModeChangeCountdown>()
        .addAll(this.countdownContext.getAll(ModeChangeCountdown.class))
        .build();
  }

  public List<ModeChangeCountdown> getSortedCountdowns(boolean includeAll) {
    return this.countdowns.stream()
        .filter(mcc -> includeAll || mcc.getRemaining() != null)
        .sorted(
            Comparator.<ModeChangeCountdown>nullsLast(
                    Comparator.comparing(MatchCountdown::getRemaining))
                .thenComparing(mcc -> mcc.getMode().getAfter()))
        .collect(Collectors.toList());
  }

  public List<ModeChangeCountdown> getActiveCountdowns() {
    return getAllCountdowns().stream()
        .filter(mcc -> mcc.getRemaining().getSeconds() > 0)
        .sorted()
        .collect(Collectors.toList());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onObjectiveModeChange(ObjectiveModeChangeEvent event) {
    if (event.isVisible()) {
      Component broadcast =
          text()
              .append(text("> > > > ", NamedTextColor.DARK_AQUA))
              .append(text(event.getName(), NamedTextColor.DARK_RED))
              .append(text(" < < < <", NamedTextColor.DARK_AQUA))
              .build();
      match.sendMessage(broadcast);
      match.playSound(SOUND);
    }
  }
}
