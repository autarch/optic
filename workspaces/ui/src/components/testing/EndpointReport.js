import React, { useMemo } from 'react';
import { useTestingService } from '../../contexts/TestingDashboardContext';
import { getOrUndefined, JsonHelper } from '@useoptic/domain';
import { makeStyles } from '@material-ui/core/styles';
import { diff } from 'react-ace';

export default function EndpointReportContainer(props) {
  const { captureId, endpoint } = props;
  const { error, loading, result: diffRegions } = useTestingService((service) =>
    service.loadEndpointDiffs(captureId, endpoint.pathId, endpoint.method)
  );

  const diffsSummary = useMemo(() => {
    if (!diffRegions) return null;
    return createEndpointsDiffSummary(diffRegions);
  });

  if (error) throw error;

  return (
    <EndpointReport
      endpointCounts={endpoint.counts}
      endpointPurpose={endpoint.descriptor.endpointPurpose}
      loadingDiffsSummary={loading}
      diffsSummary={diffsSummary}
    />
  );
}

export function EndpointReport(props) {
  const { diffsSummary, endpointPurpose, endpointCounts } = props;

  const classes = useStyles();

  return (
    <div className={classes.root}>
      <h6 className={classes.endpointPurpose}>{endpointPurpose}</h6>

      {diffsSummary && <EndpointDiffsSummary diffsSummary={diffsSummary} />}
    </div>
  );
}

function EndpointDiffsSummary({ diffsSummary }) {
  const { responses, requests } = diffsSummary;
  const classes = useStyles();

  if (diffsSummary.totalCount < 1) {
    return <div>No diffs!</div>;
  } else {
    return (
      <div className={classes.diffsContainer}>
        <div className={classes.requestStats}>
          <h4>Requests</h4>

          {requests.count < 1 ? (
            <div>No diffs!</div>
          ) : (
            <>
              {requests.regionDiffs.length > 0 && (
                <ul className={classes.diffsList}>
                  {requests.regionDiffs.map((diff) => (
                    <li key={diff.id} className={classes.diffsListItem}>
                      {diff.changeType} {diff.summary}
                    </li>
                  ))}
                </ul>
              )}

              {requests.bodyDiffs.length > 0 && (
                <ul className={classes.diffsList}>
                  {requests.bodyDiffs.map((diff) => (
                    <li key={diff.id} className={classes.diffsListItem}>
                      {diff.changeType} {diff.summary}
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>

        <div className={classes.requestStats}>
          <h4>Responses</h4>

          {responses.count < 1 ? (
            <div>No diffs!</div>
          ) : (
            <>
              {responses.regionDiffs.length > 0 && (
                <ul className={classes.diffsList}>
                  {responses.regionDiffs.map((diff) => (
                    <li key={diff.id} className={classes.diffsList}>
                      {diff.changeType} {diff.summary}
                    </li>
                  ))}
                </ul>
              )}

              {responses.bodyDiffs.length > 0 && (
                <ul className={classes.diffsList}>
                  {responses.bodyDiffs.map((diff) => (
                    <li key={diff.id} className={classes.diffsList}>
                      {diff.changeType} {diff.summary}
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>
      </div>
    );
  }
}

const useStyles = makeStyles((theme) => ({
  root: {
    padding: theme.spacing(0, 2, 3),
  },

  endpointPurpose: {
    ...theme.typography.h6,
    margin: 0,
  },

  diffsContainer: {},

  diffsList: {
    padding: 0,
    listStyleType: 'none',
  },
}));

function createEndpointsDiffSummary(diffRegions) {
  const { bodyDiffs, newRegions } = diffRegions;

  const responseNewRegions = newRegions.filter(getInResponse);
  const responseBodyDiffs = bodyDiffs.filter(getInResponse);
  const requestNewRegions = newRegions.filter(getInRequest);
  const requestBodyDiffs = bodyDiffs.filter(getInResponse);

  const requests = {
    regionDiffs: requestNewRegions.map(createRegionDiff),
    bodyDiffs: requestBodyDiffs.map(createShapeDiff),
    unmatchedContentTypes: requestNewRegions
      .filter(getContentType)
      .map(getContentType),
  };

  const responses = {
    regionDiffs: responseNewRegions.map(createRegionDiff),
    bodyDiffs: responseBodyDiffs.map(createShapeDiff),
    unmatchedStatusCodes: responseNewRegions
      .filter(getStatusCode)
      .map(getStatusCode),
    unmatchedContentTypes: responseNewRegions
      .filter(getContentType)
      .map(getContentType),
  };

  return {
    totalCount: bodyDiffs.length + newRegions.length,
    responses: {
      ...responses,
      count: responses.regionDiffs.length + responses.bodyDiffs.length,
    },
    requests: {
      ...requests,
      count: requests.regionDiffs.length + requests.bodyDiffs.length,
    },
  };

  function getInRequest(diff) {
    return diff.inRequest;
  }
  function getInResponse(diff) {
    return diff.inResponse;
  }
  function getStatusCode(diff) {
    return diff.statusCode;
  }
  function getContentType(diff) {
    return diff.contentType;
  }
  function createShapeDiff(diff) {
    return {
      id: diff.toString(),
      changeType: diff.changeType,
      count: 1, // replace with actual count
      location: JsonHelper.seqToJsArray(diff.location),
      changeType: diff.description.changeTypeAsString,
      summary: diff.description.summary,
    };
  }

  function createRegionDiff(diff) {
    return {
      id: diff.toString(),
      changeType: diff.changeType,
      count: 1, // replace with actual count
      contentType: getOrUndefined(diff.contentType),
      statusCode: getOrUndefined(diff.statusCode),
      summary: diff.description.summary || diff.description.assertion,
    };
  }
}
