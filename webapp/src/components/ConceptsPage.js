import Button from '@material-ui/core/Button';
import Tooltip from '@material-ui/core/Tooltip';
import CancelIcon from '@material-ui/icons/Cancel';
import React from 'react';
import withStyles from '@material-ui/core/styles/withStyles';
import { withRouter } from 'react-router-dom';
import { ExpansionStore } from '../contexts/ExpansionContext.js';
import { withRfcContext } from '../contexts/RfcContext';
import Typography from '@material-ui/core/Typography';
import { ShapeEditorStore, withShapeEditorContext } from '../contexts/ShapeEditorContext.js';
import { ShapesCommands } from '../engine';
import { routerUrls } from '../routes.js';
import ContributionWrapper from './contributions/ContributionWrapper.js';
import { FullSheet } from './navigation/Editor.js';
import { EditorModes, withEditorContext } from '../contexts/EditorContext';
import ContributionTextField from './contributions/ContributionTextField';
import { updateContribution } from '../engine/routines';
import Editor from './navigation/Editor';
import { track } from '../Analytics';
import Helmet from 'react-helmet';
import RequestPageHeader from './requests/RequestPageHeader.js';
import BasicButton from './shape-editor/BasicButton.js';
import { ShapeParameterName } from './shape-editor/NameInputs.js';
import { coreShapeIdsSet } from './shape-editor/ShapeUtilities.js';
import ShapeViewer, { WriteOnly } from './shape-editor/ShapeViewer.js';

export const styles = theme => ({
	root: {
		paddingLeft: 12,
		paddingRight: 12,
		paddingTop: 15
	},
	shapeEditorContainer: {
		padding: 10,
		borderRadius: 5,
		backgroundColor: '#f8f8f8'
	}
});

class ConceptsPage extends React.Component {
	renderMissing() {
		const { classes } = this.props;
		return (
			<Editor>
				<FullSheet>
					<div className={classes.root}>
						<Typography>This Concept does not exist</Typography>
					</div>
				</FullSheet>
			</Editor>
		);
	}

	componentDidUpdate(prevProps, prevState, snapshot) {
		if (prevProps.conceptId !== this.props.conceptId) {
			track('Loaded Concept');
		}
	}


	render() {
		const { history, baseUrl, classes, conceptId, handleCommand, mode, cachedQueryResults, queries } = this.props;
		const { apiName, contributions } = cachedQueryResults;

		let shape = null;
		try {
			shape = queries.shapeById(conceptId);
		} catch (e) {
			console.error(e);
			return this.renderMissing();
		}

		return (
			<Editor>
				<Helmet><title>{shape.name} -- {apiName}</title></Helmet>
				<FullSheet>
					<div className={classes.root}>
						<ContributionTextField
							key={`${conceptId}-name`}
							value={shape.name}
							variant={'heading'}
							placeholder={'Concept Name'}
							mode={mode}
							onBlur={(value) => {
								const command = ShapesCommands.RenameShape(conceptId, value);
								handleCommand(command);
							}}
						/>

						<ContributionTextField
							key={`${conceptId}-description`}
							value={contributions.getOrUndefined(conceptId, 'description')}
							variant={'multi'}
							placeholder={'Description'}
							mode={mode}
							onBlur={(value) => {
								handleCommand(updateContribution(conceptId, 'description', value));
							}}
						/>
						<ShapeEditorStore onShapeSelected={(shapeId) => {
							history.push(routerUrls.conceptPage(baseUrl, shapeId));
						}}>
							<ExpansionStore>
								<ShapeParameterManagerWrapper mode={mode} shape={shape} />
								<div className={classes.shapeEditorContainer}>
									<ShapeViewer shape={shape} />
								</div>
							</ExpansionStore>
						</ShapeEditorStore>
					</div>
				</FullSheet>
			</Editor>
		);
	}
}

function ShapeParameterManagerBase({ parameters, cachedQueryResults, shape, canManage, addParameter, removeShapeParameter }) {
	const [shouldShowControls, setShouldShowControls] = React.useState(parameters.length > 0);
	const parameterComponents = parameters
		.map(parameter => {
			return (
				<div style={{ display: 'flex', alignItems: 'center' }}>
					<div style={{ minWidth: 200 }}>
						<ShapeParameterName name={parameter.name} shapeParameterId={parameter.shapeParameterId} />
					</div>
					<div style={{ flex: 1, paddingLeft: 22 }}>
						<ContributionWrapper
							value={parameter.name}
							defaultText={''}
							variant="inline"
							placeholder={"description"}
							cachedQueryResults={cachedQueryResults}
							contributionKey="description"
							contributionParentId={parameter.shapeParameterId}
						/>
					</div>
					{canManage && <WriteOnly>
						<BasicButton onClick={() => removeShapeParameter(parameter.shapeParameterId)} style={{padding: '.5em'}}>
							<CancelIcon style={{ width: 15, color: '#a6a6a6' }} />
						</BasicButton>
					</WriteOnly>}
				</div>
			);
		});

	if (!shouldShowControls) {
		return (
			<div style={{ padding: '1em' }}>
				<WriteOnly>
					<Tooltip
						placement={'bottom-start'}
						title={`Composable Shapes allow you to create higher order concepts that you can reuse throughout your API definition. Use cases include pagination, hypermedia, or other standards important to your team.`}>
						<div>
							<Button size="small" color="secondary" onClick={() => setShouldShowControls(true)}>
								make this Shape composable</Button>
						</div>
					</Tooltip>
				</WriteOnly>
			</div>
		);
	}
	return (
		<div>
			<RequestPageHeader forType={'Parameter'} canAdd={canManage} addAction={() => addParameter(shape.shapeId)} />
			{parameterComponents.length === 0 ? <Typography>No Parameters</Typography> : parameterComponents}
			<br />
		</div>
	);
}

const ShapeParameterManager = withEditorContext(withShapeEditorContext(ShapeParameterManagerBase));

function ShapeParameterManagerWrapper({ mode, shape }) {
	const parameters = shape.parameters.filter(parameter => !parameter.isRemoved);
	const spacer = <div style={{ padding: '.5em' }}>&nbsp;</div>
	
	if (mode === EditorModes.DOCUMENTATION && parameters.length === 0) {
		return spacer;
	}
	if (parameters.length === 0 && !(shape.coreShapeId === '$object' || shape.coreShapeId === '$oneOf')) {
		return spacer;
	}
	const canManage = !coreShapeIdsSet.has(shape.shapeId);

	return (
		<div>
			<ShapeParameterManager shape={shape} parameters={parameters} canManage={canManage} />
		</div>
	);
}

export default withRouter(withEditorContext(withRfcContext(withStyles(styles)(ConceptsPage))));
