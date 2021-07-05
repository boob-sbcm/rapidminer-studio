/**
 * Copyright (C) 2001-2020 by RapidMiner and the contributors
 *
 * Complete list of developers available at our web site:
 *
 * http://rapidminer.com
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
 */
package com.rapidminer.gui.processeditor.results;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.rapidminer.adaption.belt.IOTable;
import com.rapidminer.belt.table.Table;
import com.rapidminer.core.license.ProductConstraintManager;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.set.MappedExampleSet;
import com.rapidminer.gui.renderer.DefaultTextRenderer;
import com.rapidminer.gui.renderer.Renderer;
import com.rapidminer.gui.renderer.RendererService;
import com.rapidminer.gui.tools.ProgressThread;
import com.rapidminer.gui.tools.SwingTools;
import com.rapidminer.gui.tools.components.ButtonBarCardPanel;
import com.rapidminer.gui.tools.components.CardSelectionEvent;
import com.rapidminer.gui.tools.components.CardSelectionListener;
import com.rapidminer.gui.tools.components.ResourceCard;
import com.rapidminer.license.LicenseConstants;
import com.rapidminer.license.LicenseManagerRegistry;
import com.rapidminer.license.violation.LicenseConstraintViolation;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.IOObject;
import com.rapidminer.operator.ResultObject;
import com.rapidminer.operator.nio.file.BinaryEntryFileObject;
import com.rapidminer.repository.RepositoryTools;
import com.rapidminer.repository.gui.BinaryEntryResultRendererRegistry;
import com.rapidminer.repository.gui.RendererProvider;
import com.rapidminer.tools.I18N;
import com.rapidminer.tools.LogService;
import com.rapidminer.tools.usagestats.ActionStatisticsCollector;


/**
 * Static methods to generate result visualization components etc.
 *
 * @author Simon Fischer
 * */
public class ResultDisplayTools {

	public static final String IOOBJECT_USER_DATA_KEY_RENDERER = ResultDisplayTools.class.getName() + ".renderer";

	static final String CLIENT_PROPERTY_RAPIDMINER_RESULT_NAME_HTML = "rapidminer.result.name.html";
	static final String CLIENT_PROPERTY_RAPIDMINER_RESULT_ICON = "rapidminer.result.icon";
	static final String CLIENT_PROPERTY_RAPIDMINER_RESULT_NAME = "rapidminer.result.name";

	private static final String DEFAULT_RESULT_ICON_NAME = "presentation_chart.png";

	/** the icon shown for "under construction" tabs */
	private static final ImageIcon WAIT_ICON = SwingTools.createIcon("24/hourglass.png");
	private static final ImageIcon ERROR_ICON = SwingTools.createIcon("24/error.png");

	private static Icon defaultResultIcon = null;

	/**
	 * In these cases the unnecessary additional panel is suppressed
	 */
	private static final Set<String> NO_CARD_KEYS = new HashSet<>(Arrays.asList(new String[]{"collection", "metamodel",
			"delegation_model"}));

	static {
		defaultResultIcon = SwingTools.createIcon("16/" + DEFAULT_RESULT_ICON_NAME);
	}

	public static JPanel createVisualizationComponent(IOObject resultObject, IOContainer resultContainer,
													  String usedResultName) {
		return createVisualizationComponent(resultObject, resultContainer, usedResultName, true);
	}

	/**
	 * Creates a panel which centers an error message.
	 *
	 * @param error
	 *            the message to display
	 * @return
	 */
	public static JPanel createErrorComponent(String error) {
		JPanel panel = new JPanel(new BorderLayout());
		JLabel label = new JLabel(error, ERROR_ICON, SwingConstants.CENTER);
		panel.add(label, BorderLayout.CENTER);
		return panel;
	}

	/**
	 * @param showCards
	 *            if <code>false</code> the cards on the left side of the visualization component
	 *            will not be shown
	 */
	public static JPanel createVisualizationComponent(IOObject result, final IOContainer resultContainer,
													  String usedResultName, final boolean showCards) {
		final String resultName = RendererService.getName(result.getClass());
		ButtonBarCardPanel visualisationComponent;
		Collection<Renderer> renderers = RendererService.getRenderersExcludingLegacyRenderers(resultName);

		// fallback to default toString method!
		if (resultName == null) {
			renderers.add(new DefaultTextRenderer());
		}

		// constructing panel of renderers
		visualisationComponent = new ButtonBarCardPanel(NO_CARD_KEYS, showCards);
		final ButtonBarCardPanel cardPanel = visualisationComponent;
		// check license limit for ExampleSet/IOTable rows
		final List<LicenseConstraintViolation<Integer, Integer>> violationList = new ArrayList<>();
		if (result instanceof ExampleSet) {
			LicenseConstraintViolation<Integer, Integer> violation = checkRowLimitViolation(((ExampleSet) result).size());
			if (violation != null) {
				result = downsample((ExampleSet) result, violation.getConstraintValue());
				addViolation(violation, violationList);
			}
		} else if (result instanceof IOTable) {
			LicenseConstraintViolation<Integer, Integer> violation = checkRowLimitViolation(((IOTable) result).getTable().height());
			if (violation != null) {
				result = downsample((IOTable) result, violation.getConstraintValue());
				addViolation(violation, violationList);
			}
		}
		final IOObject resultObject = result;
		// binary entry file objects can have a custom renderer associated with them, show that one first
		if (resultObject instanceof BinaryEntryFileObject) {
			BinaryEntryFileObject binaryEntryFileObject = (BinaryEntryFileObject) resultObject;
			List<RendererProvider> rendererProviders = BinaryEntryResultRendererRegistry.getInstance().getCallback(
					RepositoryTools.getSuffixFromFilename(binaryEntryFileObject.getFilename()));
			if (rendererProviders != null) {
				for (RendererProvider rendererProvider : rendererProviders) {
					addRenderer(resultContainer, cardPanel, violationList, resultObject,
							rendererProvider.getRenderer(binaryEntryFileObject), rendererProvider.getIconName(binaryEntryFileObject));
				}
			} else {
				addRenderer(resultContainer, cardPanel, violationList, resultObject, new DefaultTextRenderer(), null);
			}
		}
		// now the regular renderers (coming from the respective ioobjects.xml file)
		for (final Renderer renderer : renderers) {
			addRenderer(resultContainer, cardPanel, violationList, resultObject, renderer, null);
		}
		if (resultObject.getUserData(IOOBJECT_USER_DATA_KEY_RENDERER) != null) {
			// check for user specified settings
			visualisationComponent.selectCard(toCardName((String) resultObject.getUserData(IOOBJECT_USER_DATA_KEY_RENDERER)));
		}
		// report statistics
		visualisationComponent.addCardSelectionListener(new CardSelectionListener() {

			private String lastKey = "";

			@Override
			public void cardSelected(CardSelectionEvent e) {
				if (e != null) {
					String key = e.getCardKey();
					if (key != null && !key.equals(lastKey)) {
						ActionStatisticsCollector.getInstance().log(ActionStatisticsCollector.TYPE_RENDERER, key, "select");
						this.lastKey = key;
					}
				}
			}
		});
		// result panel
		final JPanel resultPanel = new JPanel(new BorderLayout());
		resultPanel.putClientProperty("main.component", visualisationComponent);

		resultPanel.add(visualisationComponent, BorderLayout.CENTER);

		if (resultObject instanceof ResultObject) {
			if (((ResultObject) resultObject).getResultIcon() != null) {
				resultPanel.putClientProperty(ResultDisplayTools.CLIENT_PROPERTY_RAPIDMINER_RESULT_ICON,
						((ResultObject) resultObject).getResultIcon());
			} else {
				resultPanel.putClientProperty(ResultDisplayTools.CLIENT_PROPERTY_RAPIDMINER_RESULT_ICON, defaultResultIcon);
			}
		}
		resultPanel.putClientProperty(ResultDisplayTools.CLIENT_PROPERTY_RAPIDMINER_RESULT_NAME, usedResultName);
		String source = resultObject.getSource() != null ? resultObject.getSource() : "";
		resultPanel.putClientProperty(ResultDisplayTools.CLIENT_PROPERTY_RAPIDMINER_RESULT_NAME_HTML, "<html>"
				+ usedResultName + "<br/><small>" + source + "</small></html>");
		return resultPanel;
	}

	public static ResultDisplay makeResultDisplay() {
		return new DockableResultDisplay();
	}

	/**
	 * @since 9.7
	 */
	private static void addRenderer(IOContainer resultContainer, ButtonBarCardPanel cardPanel,
									List<LicenseConstraintViolation<Integer, Integer>> violationList,
									IOObject resultObject, Renderer renderer, String customCardIcon) {
		if (renderer == null) {
			LogService.getRoot().log(Level.WARNING, () -> String.format("Renderer for %s was null!", resultObject.toString()));
			return;
		}
		String cardKey = toCardName(renderer.getName());
		final ResourceCard card = new ResourceCard(cardKey, "result_view." + cardKey);

		// custom renderers (e.g. for binary entries) can define their own icon
		if (customCardIcon != null) {
			card.setIcon(SwingTools.createIcon("32/" + customCardIcon));
		}

		// create a placeholder panel which shows "under construction" so the results can be
		// displayed immediately
		final JPanel inConstructionPanel = new JPanel(new BorderLayout());
		String humanName = I18N.getGUIMessageOrNull("gui.cards.result_view." + cardKey + ".title");
		if (humanName == null) {
			humanName = cardKey;
		}
		JLabel waitLabel = new JLabel(I18N.getGUILabel("result_construction", humanName));
		waitLabel.setIcon(WAIT_ICON);
		waitLabel.setHorizontalTextPosition(SwingConstants.TRAILING);
		waitLabel.setHorizontalAlignment(SwingConstants.CENTER);
		inConstructionPanel.add(waitLabel, BorderLayout.CENTER);
		cardPanel.addCard(card, inConstructionPanel);
		try {
			ProgressThread resultThread = new ProgressThread("creating_result_tab", false, humanName) {

				@Override
				public void run() {
					getProgressListener().setTotal(100);
					getProgressListener().setCompleted(1);

					final Component rendererComponent = renderer.getVisualizationComponent(resultObject,
							resultContainer);
					getProgressListener().setCompleted(60);

					if (rendererComponent != null) {

						if (rendererComponent instanceof JComponent) {
							((JComponent) rendererComponent).setBorder(null);
						}
						getProgressListener().setCompleted(80);

						SwingUtilities.invokeLater(() -> {
							// update container
							// renderer is finished, remove placeholder
							inConstructionPanel.removeAll();

							// add license information if necessary
							if (!violationList.isEmpty()) {
								JPanel warnPanel = new ResultLimitPanel(rendererComponent.getBackground(),
										violationList.get(0));
								inConstructionPanel.add(warnPanel, BorderLayout.NORTH);
							}

							// add real renderer
							inConstructionPanel.add(rendererComponent, BorderLayout.CENTER);

							inConstructionPanel.revalidate();
							inConstructionPanel.repaint();
						});
						getProgressListener().complete();
					}
				}
			};

			// start result calculation progress thread
			resultThread.start();
		} catch (Exception e) {
			LogService.getRoot().log(Level.WARNING,
					I18N.getMessage(LogService.getRoot().getResourceBundle(),
							"com.rapidminer.gui.processeditor.results.ResultDisplayTools.error_creating_renderer", e),
					e);
			SwingUtilities.invokeLater(() -> {
				inConstructionPanel.removeAll();
				// add error msg label
				inConstructionPanel.add(new JLabel(I18N.getMessage(I18N.getErrorBundle(), "result_display.error_creating_renderer",
						renderer.getName())), BorderLayout.CENTER);
				// make sure it's shown
				inConstructionPanel.revalidate();
				inConstructionPanel.repaint();
			});
		}
	}

	private static String toCardName(String name) {
		return name.toLowerCase().replace(' ', '_');
	}

	/**
	 * Takes the first {@code #newSize} rows of the given example set and returns a new one with
	 * only the first n rows
	 */
	private static ExampleSet downsample(ExampleSet exampleSet, int newSize) {
		int[] mapping = new int[newSize];
		for (int i = 0; i < newSize; i++) {
			mapping[i] = i;
		}
		return new MappedExampleSet(exampleSet, mapping);
	}

	/**
	 * Creates a new table consisting only of the first {@code #newSize} rows of the given table.
	 */
	private static IOTable downsample(IOTable ioTable, int newSize) {
		Table table = ioTable.getTable();
		Table downsampled = table.rows(0, newSize, new DisplayContext());
		IOTable newIOTable = new IOTable(downsampled);
		newIOTable.getAnnotations().putAll(ioTable.getAnnotations());
		newIOTable.setSource(ioTable.getSource());
		return newIOTable;
	}

	/**
	 * Checks if a data set/table of the given size violates the data row constraint.
	 */
	private static LicenseConstraintViolation<Integer, Integer> checkRowLimitViolation(int size) {
		return LicenseManagerRegistry.INSTANCE.get()
				.checkConstraintViolation(ProductConstraintManager.INSTANCE.getProduct(),
						LicenseConstants.DATA_ROW_CONSTRAINT, size, false);
	}

	/**
	 * Adds the violation to the list and logs it.
	 */
	private static void addViolation(LicenseConstraintViolation<Integer, Integer> violation,
									 List<LicenseConstraintViolation<Integer, Integer>> violationList) {
		violationList.add(violation);
		ActionStatisticsCollector.INSTANCE.log(ActionStatisticsCollector.TYPE_ROW_LIMIT,
				ActionStatisticsCollector.VALUE_ROW_LIMIT_DIALOG, "results_banner");
	}

}
