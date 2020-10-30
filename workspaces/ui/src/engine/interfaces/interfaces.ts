import { IDiff } from './diffs';

interface SerializedDiff {
  [key: string]: {
    interactionTrail: {
      path: any[];
    };
    requestsTrail: any;
  };
}

export enum ICoreShapeKinds {
  ObjectKind = '$object',
  ListKind = '$list',
  MapKind = '$map',
  OneOfKind = '$oneOf',
  AnyKind = '$any',
  StringKind = '$string',
  NumberKind = '$number',
  BooleanKind = '$boolean',
  NullableKind = '$nullable',
  OptionalKind = '$optional',
  UnknownKind = '$unknown',
}

// Diff Types the UI Handles

export const allowedDiffTypes: {
  [key: string]: {
    isBodyShapeDiff: boolean;
    inRequest: boolean;
    inResponse: boolean;
    asString: string;
  };
} = {
  UnmatchedRequestUrl: {
    isBodyShapeDiff: false,
    inRequest: false,
    inResponse: false,
    asString: 'UnmatchedRequestUrl',
  },
  UnmatchedRequestMethod: {
    isBodyShapeDiff: false,
    inRequest: false,
    inResponse: false,
    asString: 'UnmatchedRequestMethod',
  },
  UnmatchedRequestBodyContentType: {
    isBodyShapeDiff: false,
    inRequest: true,
    inResponse: false,
    asString: 'UnmatchedRequestBodyContentType',
  },
  UnmatchedResponseBodyContentType: {
    isBodyShapeDiff: false,
    inRequest: false,
    inResponse: true,
    asString: 'UnmatchedResponseBodyContentType',
  },
  UnmatchedResponseStatusCode: {
    isBodyShapeDiff: false,
    inRequest: false,
    inResponse: true,
    asString: 'UnmatchedResponseStatusCode',
  },
  UnmatchedRequestBodyShape: {
    isBodyShapeDiff: true,
    inRequest: true,
    inResponse: false,
    asString: 'UnmatchedRequestBodyShape',
  },
  UnmatchedResponseBodyShape: {
    isBodyShapeDiff: true,
    inRequest: false,
    inResponse: true,
    asString: 'UnmatchedResponseBodyShape',
  },
};

export const allowedDiffTypesKeys: string[] = Object.keys(allowedDiffTypes);

// Properties of Each Diff Types

export const isBodyShapeDiff = (key: string): boolean =>
  allowedDiffTypes[key]?.isBodyShapeDiff;
export const DiffInRequest = (key: string): boolean =>
  allowedDiffTypes[key]?.inRequest;
export const DiffInResponse = (key: string): boolean =>
  allowedDiffTypes[key]?.inResponse;

// The ones we like to work with in the UI

export interface IRequestBodyLocation {
  contentType?: string;
}

export interface IResponseBodyLocation {
  statusCode: number;
  contentType?: string;
}

export interface IParsedLocation {
  pathId: string;
  method: string;
  inRequest?: IRequestBodyLocation;
  inResponse?: IResponseBodyLocation;
}