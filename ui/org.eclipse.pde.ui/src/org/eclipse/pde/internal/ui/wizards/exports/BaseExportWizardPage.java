package org.eclipse.pde.internal.ui.wizards.exports;

import java.util.ArrayList;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.pde.core.IModel;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.elements.DefaultContentProvider;
import org.eclipse.pde.internal.ui.parts.WizardCheckboxTablePart;
import org.eclipse.pde.internal.ui.util.SWTUtil;
import org.eclipse.pde.internal.ui.wizards.ListUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;


public class BaseExportWizardPage extends WizardPage {
	private String S_EXPORT_UPDATE = "exportUpdate";
	private String S_DESTINATION = "destination";
	private String S_ADD_ZIPS = "addZips";
	private IStructuredSelection selection;
	private Combo destination;
	private ExportPart exportPart;
	private boolean featureExport;
	private Button zipRadio;
	private Button updateRadio;
	private Button browseButton;
	private Button includeChildren;

	class ExportListProvider
		extends DefaultContentProvider
		implements IStructuredContentProvider {
		public Object[] getElements(Object parent) {
			return getListElements();
		}
	}

	class ExportPart extends WizardCheckboxTablePart {
		public ExportPart(String label) {
			super(label);
		}

		public void updateCounter(int count) {
			super.updateCounter(count);
			pageChanged();
		}
	}

	public BaseExportWizardPage(
		IStructuredSelection selection,
		String name,
		String choiceLabel,
		boolean featureExport) {
		super(name);
		this.selection = selection;
		this.featureExport = featureExport;
		exportPart = new ExportPart(choiceLabel);
		setDescription(PDEPlugin.getResourceString("ExportWizard.Plugin.description"));
	}

	/**
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		container.setLayout(layout);
		exportPart.createControl(container);
		GridData gd = (GridData) exportPart.getControl().getLayoutData();
		gd.heightHint = 175;
		gd.widthHint = 150;

		createLabel(container, "", 2);
		createLabel(
			container,
			PDEPlugin.getResourceString(
				featureExport
					? "ExportWizard.Feature.label"
					: "ExportWizard.Plugin.label"),
			2);
		zipRadio =
			createRadioButton(
				container,
				PDEPlugin.getResourceString(
					featureExport
						? "ExportWizard.Feature.zip"
						: "ExportWizard.Plugin.zip"));
		updateRadio =
			createRadioButton(
				container,
				PDEPlugin.getResourceString("ExportWizard.Plugin.updateJars"));

		if (featureExport) {
			includeChildren = new Button(container, SWT.CHECK);
			includeChildren.setText(
				PDEPlugin.getResourceString("ExportWizard.Feature.include"));
			includeChildren.setSelection(true);
			includeChildren.setEnabled(false);
		}
		
		createLabel(container, "", 2);
		createLabel(
			container,
			PDEPlugin.getResourceString("ExportWizard.destination"),
			2);
		destination = new Combo(container, SWT.BORDER);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		destination.setLayoutData(gd);
		browseButton = new Button(container, SWT.PUSH);
		browseButton.setText(PDEPlugin.getResourceString("ExportWizard.browse"));
		browseButton.setLayoutData(new GridData());
		SWTUtil.setButtonDimensionHint(browseButton);
		initializeList();
		loadSettings();
		pageChanged();
		hookListeners();
		setControl(container);
		Dialog.applyDialogFont(container);
	}

	private void hookListeners() {
		destination.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				pageChanged();
			}
		});
		destination.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				pageChanged();
			}
		});
		browseButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				doBrowse();
			}
		});
	}

	private void createLabel(Composite container, String text, int span) {
		Label label = new Label(container, SWT.NULL);
		label.setText(text);
		GridData gd = new GridData();
		gd.horizontalSpan = span;
		label.setLayoutData(gd);
	}

	private Button createRadioButton(Composite container, String text) {
		Button button = new Button(container, SWT.RADIO);
		button.setText(text);
		GridData gd = new GridData();
		gd.horizontalSpan = 2;
		gd.horizontalIndent = 10;
		button.setLayoutData(gd);
		return button;
	}

	protected Object[] getListElements() {
		return new Object[0];
	}

	protected void initializeList() {
		TableViewer viewer = exportPart.getTableViewer();
		viewer.setContentProvider(new ExportListProvider());
		viewer.setLabelProvider(PDEPlugin.getDefault().getLabelProvider());
		viewer.setSorter(ListUtil.PLUGIN_SORTER);
		exportPart.getTableViewer().setInput(
			PDECore.getDefault().getWorkspaceModelManager());
		checkSelected();
	}

	private void doBrowse() {
		IPath result = chooseDestination();
		if (result != null) {
			destination.setText(result.toOSString());
		}
	}

	private IPath chooseDestination() {
		DirectoryDialog dialog = new DirectoryDialog(getShell());
		dialog.setFilterPath(destination.getText());
		dialog.setText(PDEPlugin.getResourceString("ExportWizard.dialog.title"));
		dialog.setMessage(PDEPlugin.getResourceString("ExportWizard.dialog.message"));
		String res = dialog.open();
		if (res != null) {
			return new Path(res);
		}
		return null;
	}

	protected void checkSelected() {
		Object[] elems = selection.toArray();
		ArrayList checked = new ArrayList(elems.length);

		for (int i = 0; i < elems.length; i++) {
			Object elem = elems[i];
			IProject project = null;

			if (elem instanceof IFile) {
				IFile file = (IFile) elem;
				project = file.getProject();
			} else if (elem instanceof IProject) {
				project = (IProject) elem;
			} else if (elem instanceof IJavaProject) {
				project = ((IJavaProject) elem).getProject();
			}
			if (project != null) {
				IModel model = findModelFor(project);
				if (model != null) {
					checked.add(model);
				}
			}
		}
		exportPart.setSelection(checked.toArray());
	}

	protected IModel findModelFor(IProject project) {
		WorkspaceModelManager manager = PDECore.getDefault().getWorkspaceModelManager();
		return manager.getWorkspaceModel(project);
	}

	protected void pageChanged() {
		String dest = getDestination();
		boolean hasDest = dest.length() > 0;
		boolean hasSel = exportPart.getSelectionCount() > 0;

		String message = null;
		if (!hasSel) {
			message = "No items selected.";
		} else if (!hasDest) {
			message = "Destination directory must be defined";
		}
		setMessage(message);
		setPageComplete(hasSel && hasDest);
	}

	private void loadSettings() {
		IDialogSettings settings = getDialogSettings();
		boolean exportUpdate = settings.getBoolean(S_EXPORT_UPDATE);
		zipRadio.setSelection(!exportUpdate);
		updateRadio.setSelection(exportUpdate);
		ArrayList items = new ArrayList();
		for (int i = 0; i < 6; i++) {
			String curr = settings.get(S_DESTINATION + String.valueOf(i));
			if (curr != null && !items.contains(curr)) {
				items.add(curr);
			}
		}
		destination.setItems((String[]) items.toArray(new String[items.size()]));
	}

	public void saveSettings() {
		IDialogSettings settings = getDialogSettings();
		settings.put(S_EXPORT_UPDATE, updateRadio.getSelection());
		settings.put(S_DESTINATION + String.valueOf(0), destination.getText());
		String[] items = destination.getItems();
		int nEntries = Math.min(items.length, 5);
		for (int i = 0; i < nEntries; i++) {
			settings.put(S_DESTINATION + String.valueOf(i + 1), items[i]);
		}
	}

	public Object[] getSelectedItems() {
		return exportPart.getSelection();
	}

	public boolean getExportZip() {
		return zipRadio.getSelection();
	}

	public String getDestination() {
		if (destination == null || destination.isDisposed())
			return "";
		return destination.getText();
	}
	
	public boolean getExportChildren() {
		if (featureExport) {
			return includeChildren.getSelection();
		}
		return false;
	}
}
